package com.openrouter.gateway.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.auth.JwtUtil;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.config.SecurityConfig;
import com.openrouter.gateway.usage.ModelUsageLimit;
import com.openrouter.gateway.usage.ModelUsageLimitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerUsageLimitsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // AdminController dependencies
    @MockBean private com.openrouter.gateway.logging.ChatLogRepository chatLogRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private com.openrouter.gateway.config.ModelConfigRepository modelConfigRepository;
    @MockBean private com.openrouter.gateway.config.ModelConfigService modelConfigService;
    @MockBean private com.openrouter.gateway.config.FreeModelSyncService freeModelSyncService;
    @MockBean private ModelUsageLimitRepository modelUsageLimitRepository;
    @MockBean private JwtUtil jwtUtil;

    // ── GET /api/admin/usage-limits ───────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getGlobalLimits_returnsAllGlobalRows() throws Exception {
        ModelUsageLimit row = makeLimit(1L, "meta-llama/llama-3.3-70b-instruct:free", 50, 100_000);
        when(modelUsageLimitRepository.findByUserIsNull()).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/usage-limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelId").value("meta-llama/llama-3.3-70b-instruct:free"))
                .andExpect(jsonPath("$[0].maxRequestsPerDay").value(50))
                .andExpect(jsonPath("$[0].maxTokensPerDay").value(100_000))
                .andExpect(jsonPath("$[0].userId").isEmpty());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getGlobalLimits_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/usage-limits"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/admin/usage-limits ───────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void setGlobalLimit_existingRow_updatesAndReturns200() throws Exception {
        ModelUsageLimit existing = makeLimit(1L, "meta-llama/llama-3.3-70b-instruct:free", 50, 100_000);
        ModelUsageLimit updated  = makeLimit(1L, "meta-llama/llama-3.3-70b-instruct:free", 200, 500_000);

        when(modelUsageLimitRepository.findByModelIdAndUserIsNull("meta-llama/llama-3.3-70b-instruct:free"))
                .thenReturn(Optional.of(existing));
        when(modelUsageLimitRepository.save(any())).thenReturn(updated);

        Map<String, Object> body = Map.of(
                "modelId", "meta-llama/llama-3.3-70b-instruct:free",
                "maxRequestsPerDay", 200,
                "maxTokensPerDay", 500_000
        );

        mockMvc.perform(put("/api/admin/usage-limits")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxRequestsPerDay").value(200))
                .andExpect(jsonPath("$.maxTokensPerDay").value(500_000));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void setGlobalLimit_newRow_createsAndReturns200() throws Exception {
        ModelUsageLimit created = makeLimit(5L, "google/gemma-3-27b-it:free", 30, 60_000);

        when(modelUsageLimitRepository.findByModelIdAndUserIsNull("google/gemma-3-27b-it:free"))
                .thenReturn(Optional.empty());
        when(modelUsageLimitRepository.save(argThat(l ->
                l.getModelId().equals("google/gemma-3-27b-it:free") && l.getUser() == null
        ))).thenReturn(created);

        Map<String, Object> body = Map.of(
                "modelId", "google/gemma-3-27b-it:free",
                "maxRequestsPerDay", 30,
                "maxTokensPerDay", 60_000
        );

        mockMvc.perform(put("/api/admin/usage-limits")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.modelId").value("google/gemma-3-27b-it:free"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void setGlobalLimit_missingModelId_returns400() throws Exception {
        Map<String, Object> body = Map.of("maxRequestsPerDay", 100, "maxTokensPerDay", 200_000);

        mockMvc.perform(put("/api/admin/usage-limits")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void setGlobalLimit_nonAdmin_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "modelId", "meta-llama/llama-3.3-70b-instruct:free",
                "maxRequestsPerDay", 100,
                "maxTokensPerDay", 200_000
        );

        mockMvc.perform(put("/api/admin/usage-limits")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ModelUsageLimit makeLimit(Long id, String modelId, int req, int tok) {
        ModelUsageLimit limit = new ModelUsageLimit(modelId, null, req, tok);
        // Reflectively set id (no public setter on @GeneratedValue field)
        try {
            var field = ModelUsageLimit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(limit, id);
            var updatedAt = ModelUsageLimit.class.getDeclaredField("updatedAt");
            updatedAt.setAccessible(true);
            updatedAt.set(limit, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return limit;
    }
}
