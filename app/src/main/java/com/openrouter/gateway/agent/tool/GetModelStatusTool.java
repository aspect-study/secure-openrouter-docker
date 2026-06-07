package com.openrouter.gateway.agent.tool;

import com.openrouter.gateway.config.ModelConfig;
import com.openrouter.gateway.config.ModelConfigRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetModelStatusTool implements GatewayTool {

    private final ModelConfigRepository modelConfigRepository;

    public GetModelStatusTool(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    @Override
    public String name() { return "get_model_status"; }

    @Override
    public String description() {
        return "Looks up whether a specific OpenRouter model is currently enabled on this gateway, " +
                "and when it was last used. Pass the exact model id, e.g. 'meta-llama/llama-3.3-70b-instruct:free'.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "model_id", Map.of(
                                "type", "string",
                                "description", "The exact OpenRouter model id to look up."
                        )
                ),
                "required", List.of("model_id")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object rawModelId = input.get("model_id");
        if (!(rawModelId instanceof String modelId) || modelId.isBlank()) {
            return Map.of("error", "model_id is required and must be a non-blank string");
        }
        return modelConfigRepository.findByModelId(modelId)
                .map(this::toResult)
                .orElseGet(() -> Map.of("error", "not found"));
    }

    private Map<String, Object> toResult(ModelConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", config.getModelId());
        result.put("enabled", config.isEnabled());
        result.put("lastUsedAt", config.getLastUsedAt() != null ? config.getLastUsedAt().toString() : null);
        return result;
    }
}
