package com.openrouter.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Strongly-typed configuration bound from application.properties.
 * Fails fast at startup if required values are missing or invalid.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @NotNull
    private Jwt jwt = new Jwt();

    @NotNull
    private OpenRouter openrouter = new OpenRouter();

    @NotNull
    private RateLimit rateLimit = new RateLimit();

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public OpenRouter getOpenrouter() { return openrouter; }
    public void setOpenrouter(OpenRouter openrouter) { this.openrouter = openrouter; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    // ── Nested config classes ──────────────────────────────────────────────

    public static class Jwt {
        @NotBlank
        private String secret;

        @Min(60000) // minimum 1 minute
        private long expirationMs = 86_400_000L;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }

        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    }

    public static class OpenRouter {
        @NotBlank
        private String proxyUrl = "http://localhost:8081";

        private String apiKey = "";

        public String getProxyUrl() { return proxyUrl; }
        public void setProxyUrl(String proxyUrl) { this.proxyUrl = proxyUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class RateLimit {
        @Min(1)
        private int requestsPerMinute = 10;

        @Min(1)
        private int capacity = 10;

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
    }
}
