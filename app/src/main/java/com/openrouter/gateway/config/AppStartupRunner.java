package com.openrouter.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs a free-model sync on every startup.
 *
 * Non-fatal: if OpenRouter is unreachable the app starts normally;
 * sync can be retried via POST /api/admin/sync-models.
 */
@Component
public class AppStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppStartupRunner.class);

    private final FreeModelSyncService freeModelSyncService;

    public AppStartupRunner(FreeModelSyncService freeModelSyncService) {
        this.freeModelSyncService = freeModelSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            FreeModelSyncService.SyncResult result = freeModelSyncService.syncFreeModels();
            if (result.added() > 0) {
                log.info("Startup sync: discovered={}, added={} new models (disabled pending review): {}",
                        result.discovered(), result.added(), result.newModelIds());
            } else {
                log.info("Startup sync: discovered={}, all models up to date", result.discovered());
            }
        } catch (Exception e) {
            log.warn("Startup model sync failed — continuing without sync: {}", e.getMessage());
        }
    }
}
