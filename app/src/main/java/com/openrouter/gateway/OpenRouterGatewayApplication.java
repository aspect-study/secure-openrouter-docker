package com.openrouter.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class OpenRouterGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenRouterGatewayApplication.class, args);
    }
}
