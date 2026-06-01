package com.openrouter.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer for model configuration.
 *
 * Provides a cached view of which models are currently enabled in the DB.
 * The cache ("enabledModels") is evicted whenever an admin toggles a model —
 * no TTL needed because changes are always explicit.
 *
 * Uses Spring's default ConcurrentMapCacheManager (in-memory, no Redis required).
 */
@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private final ModelConfigRepository modelConfigRepository;

    public ModelConfigService(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    /**
     * Returns the set of enabled model IDs from the DB, cached after the first call.
     * O(1) lookup for callers using Set.contains().
     */
    @Cacheable("enabledModels")
    public Set<String> getEnabledModelIds() {
        Set<String> models = modelConfigRepository.findByEnabledTrue()
                .stream()
                .map(ModelConfig::getModelId)
                .collect(Collectors.toUnmodifiableSet());
        log.debug("Loaded {} enabled models from DB into cache", models.size());
        return models;
    }

    /**
     * Evicts the enabledModels cache. Must be called after any toggle operation
     * so the next request re-populates from the DB.
     */
    @CacheEvict(value = "enabledModels", allEntries = true)
    public void evictEnabledModelsCache() {
        log.debug("enabledModels cache evicted");
    }

    /**
     * Automatically disables a model that OpenRouter reported as unavailable (404).
     *
     * Called reactively when the SSE stream receives a 404 from upstream, meaning the
     * model has been removed from OpenRouter's free tier. Disabling it prevents future
     * users from attempting to use it and keeps the model list accurate without manual
     * intervention.
     *
     * The cache is evicted so the next request re-reads from DB immediately.
     * No-op if the model is already disabled or not found in model_config.
     *
     * @param modelId the OpenRouter model ID string (e.g. "deepseek/deepseek-v4-flash:free")
     */
    @Transactional
    @CacheEvict(value = "enabledModels", allEntries = true)
    public void autoDisableRemovedModel(String modelId) {
        modelConfigRepository.findByModelId(modelId).ifPresent(model -> {
            if (model.isEnabled()) {
                model.setEnabled(false);
                modelConfigRepository.save(model);
                log.warn("Auto-disabled model '{}' — OpenRouter returned 404 (removed from free tier)", modelId);
            }
        });
    }
}
