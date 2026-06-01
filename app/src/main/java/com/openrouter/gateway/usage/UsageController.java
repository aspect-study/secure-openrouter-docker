package com.openrouter.gateway.usage;

import com.openrouter.gateway.auth.User;
import com.openrouter.gateway.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * User-facing usage endpoints.
 * Returns today's usage per model and global aggregate.
 * Requires a valid JWT (ROLE_USER or ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/user/usage")
public class UsageController {

    private static final Logger log = LoggerFactory.getLogger(UsageController.class);

    private final UsageTrackingService usageTrackingService;
    private final UserRepository userRepository;

    public UsageController(UsageTrackingService usageTrackingService,
                           UserRepository userRepository) {
        this.usageTrackingService = usageTrackingService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/user/usage
     * Returns today's usage summary: per-model list + global aggregate.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> todayUsage(
            @AuthenticationPrincipal String userEmail) {

        User user = loadUser(userEmail);
        List<UserModelUsage> rows = usageTrackingService.getUserUsageSummary(user.getId());

        int totalRequests = rows.stream().mapToInt(UserModelUsage::getRequestCount).sum();
        int totalTokens   = rows.stream().mapToInt(UserModelUsage::getTokenCount).sum();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String resetAtStr = today.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<Map<String, Object>> modelRows = rows.stream()
                .map(r -> buildModelRow(user.getId(), r))
                .toList();

        return ResponseEntity.ok(Map.of(
                "date",    today.toString(),
                "resetAt", resetAtStr,
                "globalAggregate", Map.of(
                        "totalRequests", totalRequests,
                        "totalTokens",   totalTokens
                ),
                "models", modelRows
        ));
    }

    /**
     * GET /api/user/usage/{modelId}
     * Returns today's usage for a specific model.
     */
    @GetMapping("/{modelId}")
    public ResponseEntity<Map<String, Object>> modelUsage(
            @AuthenticationPrincipal String userEmail,
            @PathVariable String modelId) {

        User user = loadUser(userEmail);
        UserModelUsage usage = usageTrackingService.getModelUsage(user.getId(), modelId);
        return ResponseEntity.ok(buildModelRow(user.getId(), usage));
    }

    // ── Private ───────────────────────────────────────────────────────────

    private Map<String, Object> buildModelRow(Long userId, UserModelUsage r) {
        int[] limits = usageTrackingService.resolveLimits(userId, r.getModelId());
        int maxRequests = limits[0];
        int maxTokens   = limits[1];

        // Determine whether this limit came from a user override or global default
        String limitSource = usageTrackingService.resolveLimits(userId, r.getModelId()) != null
                ? "GLOBAL" : "USER_OVERRIDE";

        return Map.of(
                "modelId",           r.getModelId(),
                "requests",          r.getRequestCount(),
                "maxRequests",       maxRequests,
                "tokens",            r.getTokenCount(),
                "maxTokens",         maxTokens,
                "requestsRemaining", Math.max(0, maxRequests - r.getRequestCount()),
                "tokensRemaining",   Math.max(0, maxTokens   - r.getTokenCount()),
                "resetAt",           r.getResetAt().atOffset(ZoneOffset.UTC)
                                      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }
}
