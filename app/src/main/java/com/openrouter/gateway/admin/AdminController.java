package com.openrouter.gateway.admin;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.config.FreeModelSyncService;
import com.openrouter.gateway.config.ModelConfig;
import com.openrouter.gateway.config.ModelConfigRepository;
import com.openrouter.gateway.config.ModelConfigService;
import com.openrouter.gateway.logging.ChatLog;
import com.openrouter.gateway.logging.ChatLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-only REST endpoints.
 * All methods require ROLE_ADMIN enforced via @PreAuthorize.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ChatLogRepository chatLogRepository;
    private final UserRepository userRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ModelConfigService modelConfigService;
    private final FreeModelSyncService freeModelSyncService;

    public AdminController(ChatLogRepository chatLogRepository,
                           UserRepository userRepository,
                           ModelConfigRepository modelConfigRepository,
                           ModelConfigService modelConfigService,
                           FreeModelSyncService freeModelSyncService) {
        this.chatLogRepository = chatLogRepository;
        this.userRepository = userRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.modelConfigService = modelConfigService;
        this.freeModelSyncService = freeModelSyncService;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long todayRequests = chatLogRepository.countByCreatedAtAfter(startOfDay);
        long todayTokens = chatLogRepository.sumTotalTokensSince(startOfDay);
        long activeUsers = userRepository.countByActiveTrue();
        String topModel = chatLogRepository.findTopModelToday(startOfDay);
        List<Map<String, Object>> last7Days = chatLogRepository.countByDayLast7Days();

        return ResponseEntity.ok(Map.of(
                "todayRequests", todayRequests,
                "todayTokens", todayTokens,
                "activeUsers", activeUsers,
                "topModel", topModel != null ? topModel : "N/A",
                "requestsLast7Days", last7Days
        ));
    }

    // ── Chat Logs ─────────────────────────────────────────────────────────

    @GetMapping("/chat-logs")
    public ResponseEntity<Page<ChatLogDto>> chatLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatLog> result = chatLogRepository.findWithFilters(
                user, model,
                from != null ? from.atStartOfDay() : null,
                to != null ? to.plusDays(1).atStartOfDay() : null,
                pageable);

        return ResponseEntity.ok(result.map(ChatLogDto::from));
    }

    @GetMapping("/chat-logs/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"chat-logs-" +
                LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv\"");

        PrintWriter writer = response.getWriter();
        writer.println("id,userEmail,model,promptTokens,completionTokens,totalTokens,latencyMs,statusCode,createdAt");

        chatLogRepository.findAll(Sort.by("createdAt").descending()).forEach(log ->
                writer.printf("%d,%s,%s,%d,%d,%d,%d,%d,%s%n",
                        log.getId(), log.getUserEmail(), log.getModel(),
                        log.getPromptTokens(), log.getCompletionTokens(), log.getTotalTokens(),
                        log.getLatencyMs(), log.getStatusCode(), log.getCreatedAt())
        );
        writer.flush();
    }

    // ── Models ────────────────────────────────────────────────────────────

    @GetMapping("/models")
    public ResponseEntity<List<ModelConfigDto>> models() {
        return ResponseEntity.ok(
                modelConfigRepository.findAllByOrderByModelIdAsc()
                        .stream().map(ModelConfigDto::from).toList()
        );
    }

    @PutMapping("/models/toggle")
    public ResponseEntity<ModelConfigDto> toggleModel(
            @RequestBody java.util.Map<String, String> body) {
        String modelId = body.get("modelId");
        if (modelId == null || modelId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ModelConfig config = modelConfigRepository.findByModelId(modelId).orElse(null);
        if (config == null) return ResponseEntity.notFound().build();
        config.setEnabled(!config.isEnabled());
        ModelConfig saved = modelConfigRepository.save(config);
        // Evict the enabled-models cache so the next request re-reads from DB
        modelConfigService.evictEnabledModelsCache();
        log.info("Model {} {}", modelId, saved.isEnabled() ? "enabled" : "disabled");
        return ResponseEntity.ok(ModelConfigDto.from(saved));
    }

    // ── Model Sync ────────────────────────────────────────────────────────

    @PostMapping("/sync-models")
    public ResponseEntity<SyncResultDto> syncModels() {
        try {
            FreeModelSyncService.SyncResult result = freeModelSyncService.syncFreeModels();
            log.info("Admin triggered model sync: discovered={}, added={}", result.discovered(), result.added());
            return ResponseEntity.ok(new SyncResultDto(result.discovered(), result.added(), result.newModelIds()));
        } catch (Exception e) {
            log.error("Admin model sync failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Users ─────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> users() {
        return ResponseEntity.ok(
                userRepository.findAll(Sort.by("createdAt").descending())
                        .stream().map(u -> UserDto.from(u,
                                chatLogRepository.countByUserEmail(u.getEmail())))
                        .toList()
        );
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserDto> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String role = body.get("role");
        try {
            user.setRole(User.Role.valueOf(role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        User saved = userRepository.save(user);
        log.info("User {} role changed to {}", saved.getEmail(), role);
        return ResponseEntity.ok(UserDto.from(saved,
                chatLogRepository.countByUserEmail(saved.getEmail())));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<UserDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        boolean active = Boolean.TRUE.equals(body.get("active"));
        user.setActive(active);
        User saved = userRepository.save(user);
        log.info("User {} {}", saved.getEmail(), active ? "activated" : "deactivated");
        return ResponseEntity.ok(UserDto.from(saved,
                chatLogRepository.countByUserEmail(saved.getEmail())));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public record ChatLogDto(Long id, String userEmail, String model,
                              int promptTokens, int completionTokens, int totalTokens,
                              long latencyMs, int statusCode, String responsePreview,
                              String createdAt) {
        public static ChatLogDto from(ChatLog l) {
            return new ChatLogDto(l.getId(), l.getUserEmail(), l.getModel(),
                    l.getPromptTokens(), l.getCompletionTokens(), l.getTotalTokens(),
                    l.getLatencyMs(), l.getStatusCode(), l.getResponsePreview(),
                    l.getCreatedAt().toString());
        }
    }

    public record ModelConfigDto(Long id, String modelId, boolean enabled,
                                  String lastUsedAt, String createdAt) {
        public static ModelConfigDto from(ModelConfig m) {
            return new ModelConfigDto(m.getId(), m.getModelId(), m.isEnabled(),
                    m.getLastUsedAt() != null ? m.getLastUsedAt().toString() : null,
                    m.getCreatedAt().toString());
        }
    }

    public record SyncResultDto(int discovered, int added, List<String> newModelIds) {}

    public record UserDto(Long id, String email, String role, boolean active,
                           long totalRequests, boolean keyConfigured, String createdAt) {
        public static UserDto from(User u, long totalRequests) {
            return new UserDto(u.getId(), u.getEmail(), u.getRole().name(),
                    u.isActive(), totalRequests,
                    u.getOpenrouterKeyEncrypted() != null && u.isOpenrouterKeyValidated(),
                    u.getCreatedAt().toString());
        }
    }

}
