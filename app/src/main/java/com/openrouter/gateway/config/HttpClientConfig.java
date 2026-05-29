package com.openrouter.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configures the Java 11+ built-in HttpClient for calling the nginx proxy.
 *
 * Why HttpClient over RestTemplate/WebClient?
 * - RestTemplate: blocking, being deprecated in newer Spring versions.
 * - WebClient: reactive, adds Project Reactor overhead we don't need here.
 * - HttpClient: blocking, standard JDK, pairs perfectly with virtual threads.
 *   With spring.threads.virtual.enabled=true, blocking calls run on virtual
 *   threads — giving us high concurrency without reactive complexity.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // FOLLOW_REDIRECTS in case proxy returns a redirect
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
