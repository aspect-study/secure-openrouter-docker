package com.openrouter.gateway.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.apikey.OpenRouterKeyService;
import com.openrouter.gateway.chat.OpenRouterClient;
import com.openrouter.gateway.config.AppProperties;
import com.openrouter.gateway.exception.AllModelsUnavailableException;
import com.openrouter.gateway.preferences.UserModelDto;
import com.openrouter.gateway.preferences.UserModelPreferenceService;
import com.openrouter.gateway.ratelimit.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    private final UserModelPreferenceService preferenceService;
    private final OpenRouterKeyService keyService;
    private final OpenRouterClient openRouterClient;
    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public OrchestratorService(UserModelPreferenceService preferenceService,
                                OpenRouterKeyService keyService,
                                OpenRouterClient openRouterClient,
                                RateLimitService rateLimitService,
                                AppProperties appProperties,
                                ObjectMapper objectMapper) {
        this.preferenceService = preferenceService;
        this.keyService = keyService;
        this.openRouterClient = openRouterClient;
        this.rateLimitService = rateLimitService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public void stream(String prompt, Long userId, boolean isAdmin,
                       String userEmail, SseEmitter emitter) {
        try {
            String apiKey = keyService.getKeyForUser(userEmail);

            List<UserModelDto> models = preferenceService
                    .getEffectiveModels(userId, isAdmin)
                    .models().stream()
                    .filter(UserModelDto::effectivelyEnabled)
                    .toList();

            if (models.isEmpty()) {
                sendSseEvent(emitter, "error",
                        Map.of("error", "No enabled models found. Enable models in My Models."));
                return;
            }

            if (!rateLimitService.tryConsumeN(userEmail, models.size())) {
                sendSseEvent(emitter, "error",
                        Map.of("error", "Rate limit exceeded. Please wait and try again."));
                return;
            }

            AtomicInteger successCount = new AtomicInteger(0);
            long startMs = System.currentTimeMillis();

            List<CompletableFuture<OrchestratorResult>> futures = models.stream()
                    .map(m -> CompletableFuture.supplyAsync(
                            () -> openRouterClient.queryModel(m.modelId(), m.name(), prompt, apiKey),
                            VIRTUAL))
                    .toList();

            // whenComplete returns a NEW future — collect those and wait on them so
            // successCount increments and SSE emissions are guaranteed to finish before all_done.
            List<CompletableFuture<OrchestratorResult>> tracked = futures.stream()
                    .map(f -> f.whenComplete((result, ex) -> {
                        if (result != null && "SUCCESS".equals(result.status())) {
                            successCount.incrementAndGet();
                            sendSseEvent(emitter, "model_response", result);
                        }
                    }))
                    .toList();

            CompletableFuture.allOf(tracked.toArray(new CompletableFuture[0])).join();

            sendSseEvent(emitter, "all_done", Map.of(
                    "successCount", successCount.get(),
                    "totalModels", models.size(),
                    "totalMs", System.currentTimeMillis() - startMs));

        } catch (Exception e) {
            log.error("Orchestrator stream error for {}: {}", userEmail, e.getMessage(), e);
            sendSseEvent(emitter, "error", Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Unexpected error. Please try again."));
        } finally {
            emitter.complete();
        }
    }

    public SynthesisResponse synthesize(SynthesisRequest request, String userEmail,
                                         Long userId, boolean isAdmin) {
        if (!rateLimitService.tryConsume(userEmail)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please wait and try again.");
        }

        String apiKey = keyService.getKeyForUser(userEmail);
        String defaultModel = appProperties.getOpenrouter().getSynthesisModel();

        List<UserModelDto> enabledModels = preferenceService
                .getEffectiveModels(userId, isAdmin)
                .models().stream()
                .filter(UserModelDto::effectivelyEnabled)
                .toList();

        if (enabledModels.isEmpty()) {
            throw new AllModelsUnavailableException("No enabled models available for synthesis.");
        }

        // Default synthesis model first; remaining models as ordered fallback
        List<UserModelDto> candidates = enabledModels.stream()
                .sorted(Comparator.comparing(m -> !m.modelId().equals(defaultModel)))
                .toList();

        String metaPrompt = buildSynthesisPrompt(request.prompt(), request.responses());

        for (UserModelDto candidate : candidates) {
            OrchestratorResult result = openRouterClient.queryModel(
                    candidate.modelId(), candidate.name(), metaPrompt, apiKey);
            if ("SUCCESS".equals(result.status())) {
                log.info("Synthesis complete via model: {}", candidate.modelId());
                return new SynthesisResponse(result.content(), candidate.modelId(), candidate.name());
            }
            log.warn("synthesize: {} failed ({}), trying next", candidate.modelId(), result.status());
        }

        throw new AllModelsUnavailableException(
                "All " + candidates.size() + " model(s) exhausted during synthesis. Please try again.");
    }

    private String buildSynthesisPrompt(String originalPrompt, List<OrchestratorResult> responses) {
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple AI models were asked the following question:\n\n");
        sb.append("QUESTION: ").append(originalPrompt).append("\n\n");
        sb.append("THEIR RESPONSES:\n\n");
        responses.forEach(r -> {
            sb.append("--- ").append(r.name()).append(" ---\n");
            sb.append(r.content()).append("\n\n");
        });
        sb.append("TASK: Synthesize these responses into one clear, accurate answer. ");
        sb.append("Note where models agree, and flag any significant disagreements.");
        return sb.toString();
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("SSE emit failed (client likely disconnected): {}", e.getMessage());
        }
    }
}
