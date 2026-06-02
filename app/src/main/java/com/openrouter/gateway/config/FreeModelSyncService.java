package com.openrouter.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Syncs free models from the OpenRouter public models API into model_config.
 *
 * New models are inserted with enabled=false — admin must explicitly enable them
 * after review. Sync is idempotent: existing model IDs are never touched.
 *
 * Called automatically on startup (AppStartupRunner) and on-demand via
 * POST /api/admin/sync-models.
 */
@Service
public class FreeModelSyncService {

    private static final Logger log = LoggerFactory.getLogger(FreeModelSyncService.class);
    private static final String OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";

    public record SyncResult(int discovered, int added, List<String> newModelIds) {}

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ModelConfigRepository modelConfigRepository;
    private final ModelConfigService modelConfigService;
    private final String apiKey;

    public FreeModelSyncService(HttpClient httpClient,
                                ObjectMapper objectMapper,
                                ModelConfigRepository modelConfigRepository,
                                ModelConfigService modelConfigService,
                                AppProperties appProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.modelConfigRepository = modelConfigRepository;
        this.modelConfigService = modelConfigService;
        this.apiKey = appProperties.getOpenrouter().getApiKey();
    }

    /**
     * Fetches all free models from OpenRouter, finds ones not yet in model_config,
     * and inserts them as disabled rows.
     *
     * @return SyncResult with counts and list of newly added model IDs
     * @throws Exception if the OpenRouter API call fails
     */
    @Transactional
    public SyncResult syncFreeModels() throws Exception {
        List<String> freeModelIds = fetchFreeModelIds();

        Set<String> existing = modelConfigRepository.findAllByOrderByModelIdAsc()
                .stream()
                .map(ModelConfig::getModelId)
                .collect(Collectors.toSet());

        List<String> newModelIds = freeModelIds.stream()
                .filter(id -> !existing.contains(id))
                .collect(Collectors.toList());

        for (String modelId : newModelIds) {
            ModelConfig config = new ModelConfig();
            config.setModelId(modelId);
            config.setEnabled(false);
            modelConfigRepository.save(config);
            log.info("Added new free model (disabled): {}", modelId);
        }

        if (!newModelIds.isEmpty()) {
            modelConfigService.evictEnabledModelsCache();
        }

        return new SyncResult(freeModelIds.size(), newModelIds.size(), newModelIds);
    }

    private List<String> fetchFreeModelIds() throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(OPENROUTER_MODELS_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException(
                    "OpenRouter models API returned %d".formatted(response.statusCode()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        List<String> freeIds = new ArrayList<>();

        for (JsonNode model : root.path("data")) {
            String id = model.path("id").asText();
            if (id.isBlank()) continue;

            JsonNode pricing = model.path("pricing");
            String promptPrice = pricing.path("prompt").asText("1");
            String completionPrice = pricing.path("completion").asText("1");

            boolean isFree = id.endsWith(":free")
                    || ("0".equals(promptPrice) && "0".equals(completionPrice));

            if (isFree) {
                freeIds.add(id);
            }
        }

        log.debug("Fetched {} free model IDs from OpenRouter", freeIds.size());
        return freeIds;
    }
}
