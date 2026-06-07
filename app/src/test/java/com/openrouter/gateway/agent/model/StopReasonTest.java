package com.openrouter.gateway.agent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StopReasonTest {

    @Test
    void mapsOpenAiFinishReasonStopToEndTurn() {
        assertThat(StopReason.fromOpenAiFinishReason("stop")).isEqualTo(StopReason.END_TURN);
    }

    @Test
    void mapsOpenAiFinishReasonToolCallsToToolUse() {
        assertThat(StopReason.fromOpenAiFinishReason("tool_calls")).isEqualTo(StopReason.TOOL_USE);
    }

    @Test
    void mapsOpenAiFinishReasonLengthToMaxTokens() {
        assertThat(StopReason.fromOpenAiFinishReason("length")).isEqualTo(StopReason.MAX_TOKENS);
    }

    @Test
    void mapsUnrecognisedFinishReasonToUnknown() {
        assertThat(StopReason.fromOpenAiFinishReason("content_filter")).isEqualTo(StopReason.UNKNOWN);
    }

    @Test
    void mapsNullFinishReasonToUnknown() {
        assertThat(StopReason.fromOpenAiFinishReason(null)).isEqualTo(StopReason.UNKNOWN);
    }
}
