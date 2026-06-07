package com.openrouter.gateway.agent.tool;

import com.openrouter.gateway.logging.ChatLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetGatewayStatsTool implements GatewayTool {

    private final ChatLogRepository chatLogRepository;

    public GetGatewayStatsTool(ChatLogRepository chatLogRepository) {
        this.chatLogRepository = chatLogRepository;
    }

    @Override
    public String name() { return "get_gateway_stats"; }

    @Override
    public String description() {
        return "Returns aggregate gateway usage statistics for a single UTC calendar day: " +
                "total requests, total tokens, the most-used model, and the number of distinct active users. " +
                "Pass an optional date in YYYY-MM-DD format; omit it to get today's stats.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "date", Map.of(
                                "type", "string",
                                "description", "Optional date in YYYY-MM-DD format (UTC). Defaults to today if omitted."
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object rawDate = input.get("date");
        LocalDate date;

        if (rawDate == null || (rawDate instanceof String blank && blank.isBlank())) {
            date = LocalDate.now(ZoneOffset.UTC);
        } else if (rawDate instanceof String dateString) {
            try {
                date = LocalDate.parse(dateString);
            } catch (DateTimeParseException e) {
                return Map.of("error", "date must be in YYYY-MM-DD format");
            }
        } else {
            return Map.of("error", "date must be in YYYY-MM-DD format");
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        long totalRequests = chatLogRepository.countByCreatedAtBetween(start, end);
        long totalTokens = chatLogRepository.sumTotalTokensBetween(start, end);
        String topModel = chatLogRepository.findTopModelBetween(start, end);
        long activeUsers = chatLogRepository.countDistinctUsersBetween(start, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.toString());
        result.put("totalRequests", totalRequests);
        result.put("totalTokens", totalTokens);
        result.put("topModel", topModel != null ? topModel : "N/A");
        result.put("activeUsers", activeUsers);
        return result;
    }
}
