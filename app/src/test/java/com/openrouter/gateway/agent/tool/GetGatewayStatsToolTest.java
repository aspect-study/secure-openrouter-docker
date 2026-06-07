package com.openrouter.gateway.agent.tool;

import com.openrouter.gateway.logging.ChatLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGatewayStatsToolTest {

    @Mock
    private ChatLogRepository chatLogRepository;

    @InjectMocks
    private GetGatewayStatsTool tool;

    @Test
    void returnsAggregatesForExplicitlyGivenDate() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 2, 0, 0);

        when(chatLogRepository.countByCreatedAtBetween(start, end)).thenReturn(42L);
        when(chatLogRepository.sumTotalTokensBetween(start, end)).thenReturn(18_500L);
        when(chatLogRepository.findTopModelBetween(start, end)).thenReturn("google/gemma-4-31b-it:free");
        when(chatLogRepository.countDistinctUsersBetween(start, end)).thenReturn(7L);

        Map<String, Object> result = tool.execute(Map.of("date", "2026-06-01"));

        assertThat(result)
                .containsEntry("date", "2026-06-01")
                .containsEntry("totalRequests", 42L)
                .containsEntry("totalTokens", 18_500L)
                .containsEntry("topModel", "google/gemma-4-31b-it:free")
                .containsEntry("activeUsers", 7L);
    }

    @Test
    void defaultsToTodayUtcWhenDateIsOmitted() {
        when(chatLogRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(chatLogRepository.sumTotalTokensBetween(any(), any())).thenReturn(0L);
        when(chatLogRepository.findTopModelBetween(any(), any())).thenReturn(null);
        when(chatLogRepository.countDistinctUsersBetween(any(), any())).thenReturn(0L);

        Map<String, Object> result = tool.execute(Map.of());

        assertThat(result)
                .containsEntry("date", LocalDate.now(ZoneOffset.UTC).toString())
                .containsEntry("topModel", "N/A");
    }

    @Test
    void returnsErrorWhenDateIsMalformed() {
        Map<String, Object> result = tool.execute(Map.of("date", "not-a-date"));

        assertThat(result).containsEntry("error", "date must be in YYYY-MM-DD format");
    }
}
