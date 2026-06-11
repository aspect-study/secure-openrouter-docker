package com.openrouter.gateway.ratelimit;

import com.openrouter.gateway.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getRateLimit().setCapacity(10);
        props.getRateLimit().setRequestsPerMinute(10);
        service = new RateLimitService(props);
    }

    @Test
    void tryConsumeN_consumesNTokensAtOnce() {
        assertThat(service.tryConsumeN("user@test.com", 3)).isTrue();
        assertThat(service.availableTokens("user@test.com")).isEqualTo(7);
    }

    @Test
    void tryConsumeN_returnsFalseWhenInsufficientTokens() {
        // 11 > capacity of 10 — must fail atomically (no tokens consumed)
        assertThat(service.tryConsumeN("user@test.com", 11)).isFalse();
        assertThat(service.availableTokens("user@test.com")).isEqualTo(10);
    }

    @Test
    void tryConsumeN_consumingExactCapacitySucceeds() {
        assertThat(service.tryConsumeN("user@test.com", 10)).isTrue();
        assertThat(service.availableTokens("user@test.com")).isEqualTo(0);
    }
}
