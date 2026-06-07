package com.openrouter.gateway.agent.tool;

import com.openrouter.gateway.config.ModelConfig;
import com.openrouter.gateway.config.ModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetModelStatusToolTest {

    @Mock
    private ModelConfigRepository modelConfigRepository;

    @InjectMocks
    private GetModelStatusTool tool;

    @Test
    void returnsEnabledStatusAndLastUsedAtWhenModelExists() {
        ModelConfig config = new ModelConfig();
        config.setModelId("meta-llama/llama-3.3-70b-instruct:free");
        config.setEnabled(true);
        config.setLastUsedAt(LocalDateTime.of(2026, 6, 1, 12, 0));

        when(modelConfigRepository.findByModelId("meta-llama/llama-3.3-70b-instruct:free"))
                .thenReturn(Optional.of(config));

        Map<String, Object> result = tool.execute(Map.of("model_id", "meta-llama/llama-3.3-70b-instruct:free"));

        assertThat(result)
                .containsEntry("modelId", "meta-llama/llama-3.3-70b-instruct:free")
                .containsEntry("enabled", true)
                .containsEntry("lastUsedAt", "2026-06-01T12:00");
    }

    @Test
    void returnsNullLastUsedAtWhenModelHasNeverBeenUsed() {
        ModelConfig config = new ModelConfig();
        config.setModelId("google/gemma-4-31b-it:free");
        config.setEnabled(false);
        config.setLastUsedAt(null);

        when(modelConfigRepository.findByModelId("google/gemma-4-31b-it:free"))
                .thenReturn(Optional.of(config));

        Map<String, Object> result = tool.execute(Map.of("model_id", "google/gemma-4-31b-it:free"));

        assertThat(result)
                .containsEntry("enabled", false)
                .containsEntry("lastUsedAt", null);
    }

    @Test
    void returnsErrorWhenModelIsNotFound() {
        when(modelConfigRepository.findByModelId("nonexistent/model:free")).thenReturn(Optional.empty());

        Map<String, Object> result = tool.execute(Map.of("model_id", "nonexistent/model:free"));

        assertThat(result).containsEntry("error", "not found");
    }

    @Test
    void returnsErrorWhenModelIdIsMissing() {
        Map<String, Object> result = tool.execute(Map.of());

        assertThat(result).containsEntry("error", "model_id is required and must be a non-blank string");
    }
}
