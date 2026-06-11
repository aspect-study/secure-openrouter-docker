package com.openrouter.gateway.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import com.openrouter.gateway.chat.OpenRouterClient;
import com.openrouter.gateway.config.AppProperties;
import com.openrouter.gateway.exception.AllModelsUnavailableException;
import com.openrouter.gateway.exception.KeyNotConfiguredException;
import com.openrouter.gateway.preferences.UserModelDto;
import com.openrouter.gateway.preferences.UserModelPreferenceService;
import com.openrouter.gateway.preferences.UserModelsResponse;
import com.openrouter.gateway.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock private UserModelPreferenceService preferenceService;
    @Mock private OpenRouterKeyService keyService;
    @Mock private OpenRouterClient openRouterClient;
    @Mock private RateLimitService rateLimitService;
    @Mock private SseEmitter emitter;

    private AppProperties appProperties;
    private OrchestratorService service;

    // Default synthesis model matches MODEL_A so it is tried first
    private static final String SYNTHESIS_MODEL = "mistralai/mistral-7b-instruct:free";
    // UserModelDto(Long id, String modelId, String name, boolean adminEnabled, boolean userEnabled, boolean effectivelyEnabled)
    private static final UserModelDto MODEL_A = new UserModelDto(
            1L, "mistralai/mistral-7b-instruct:free", "Mistral 7B", true, true, true);
    private static final UserModelDto MODEL_B = new UserModelDto(
            2L, "meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B", true, true, true);

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getOpenrouter().setSynthesisModel(SYNTHESIS_MODEL);
        service = new OrchestratorService(
                preferenceService, keyService, openRouterClient,
                rateLimitService, appProperties, new ObjectMapper());
    }

    // ── stream() ─────────────────────────────────────────────────────────────

    @Test
    void stream_queriesAllEffectiveModels_andCompletesEmitter() throws Exception {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A, MODEL_B), 2, 2));
        when(rateLimitService.tryConsumeN("user@test.com", 2)).thenReturn(true);
        when(openRouterClient.queryModel(eq(MODEL_A.modelId()), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), "4", 100L, "SUCCESS"));
        when(openRouterClient.queryModel(eq(MODEL_B.modelId()), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_B.modelId(), MODEL_B.name(), "Four", 200L, "SUCCESS"));

        service.stream("What is 2+2?", 1L, false, "user@test.com", emitter);

        // 2 model_response + 1 all_done = 3 send() calls
        verify(openRouterClient, times(2)).queryModel(anyString(), anyString(), anyString(), anyString());
        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void stream_allModelsFail_sendsAllDoneWithZeroCount() throws Exception {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A), 1, 1));
        when(rateLimitService.tryConsumeN("user@test.com", 1)).thenReturn(true);
        when(openRouterClient.queryModel(any(), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), null, 100L, "TIMEOUT"));

        service.stream("Hello", 1L, false, "user@test.com", emitter);

        // Only all_done sent — no model_response since all models failed
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void stream_noApiKey_sendsErrorEventAndCompletes() throws Exception {
        when(keyService.getKeyForUser("user@test.com"))
                .thenThrow(new KeyNotConfiguredException());

        service.stream("Hello", 1L, false, "user@test.com", emitter);

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(openRouterClient, never()).queryModel(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void stream_rateLimitExceeded_sendsErrorEventAndCompletes() throws Exception {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A), 1, 1));
        when(rateLimitService.tryConsumeN("user@test.com", 1)).thenReturn(false);

        service.stream("Hello", 1L, false, "user@test.com", emitter);

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(openRouterClient, never()).queryModel(anyString(), anyString(), anyString(), anyString());
    }

    // ── synthesize() ──────────────────────────────────────────────────────────

    @Test
    void synthesize_defaultModelSucceeds_returnsResultWithModelName() {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A, MODEL_B), 2, 2));
        when(openRouterClient.queryModel(eq(MODEL_A.modelId()), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), "Merged answer", 300L, "SUCCESS"));
        when(rateLimitService.tryConsume("user@test.com")).thenReturn(true);

        SynthesisRequest request = new SynthesisRequest("What is 2+2?",
                List.of(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), "4", 100L, "SUCCESS")));

        SynthesisResponse response = service.synthesize(request, "user@test.com", 1L, false);

        assertThat(response.content()).isEqualTo("Merged answer");
        assertThat(response.modelId()).isEqualTo(MODEL_A.modelId());
        assertThat(response.modelName()).isEqualTo(MODEL_A.name());
        // MODEL_B should never be called
        verify(openRouterClient, never()).queryModel(eq(MODEL_B.modelId()), any(), any(), any());
    }

    @Test
    void synthesize_defaultModelFails_fallsBackToNext() {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A, MODEL_B), 2, 2));
        when(openRouterClient.queryModel(eq(MODEL_A.modelId()), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), null, 100L, "TIMEOUT"));
        when(openRouterClient.queryModel(eq(MODEL_B.modelId()), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_B.modelId(), MODEL_B.name(), "Fallback answer", 200L, "SUCCESS"));
        when(rateLimitService.tryConsume("user@test.com")).thenReturn(true);

        SynthesisRequest request = new SynthesisRequest("What is 2+2?",
                List.of(new OrchestratorResult(MODEL_B.modelId(), MODEL_B.name(), "4", 100L, "SUCCESS")));

        SynthesisResponse response = service.synthesize(request, "user@test.com", 1L, false);

        assertThat(response.content()).isEqualTo("Fallback answer");
        assertThat(response.modelId()).isEqualTo(MODEL_B.modelId());
    }

    @Test
    void synthesize_allModelsFail_throwsAllModelsUnavailable() {
        when(keyService.getKeyForUser("user@test.com")).thenReturn("sk-or-test");
        when(preferenceService.getEffectiveModels(1L, false))
                .thenReturn(new UserModelsResponse(List.of(MODEL_A), 1, 1));
        when(openRouterClient.queryModel(any(), any(), any(), any()))
                .thenReturn(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), null, 100L, "TIMEOUT"));
        when(rateLimitService.tryConsume("user@test.com")).thenReturn(true);

        SynthesisRequest request = new SynthesisRequest("What is 2+2?",
                List.of(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), "4", 100L, "SUCCESS")));

        assertThatThrownBy(() -> service.synthesize(request, "user@test.com", 1L, false))
                .isInstanceOf(AllModelsUnavailableException.class);
    }

    @Test
    void synthesize_rateLimitExceeded_throws429() {
        when(rateLimitService.tryConsume("user@test.com")).thenReturn(false);

        SynthesisRequest request = new SynthesisRequest("What is 2+2?",
                List.of(new OrchestratorResult(MODEL_A.modelId(), MODEL_A.name(), "4", 100L, "SUCCESS")));

        assertThatThrownBy(() -> service.synthesize(request, "user@test.com", 1L, false))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("429");
        verify(openRouterClient, never()).queryModel(anyString(), anyString(), anyString(), anyString());
    }
}
