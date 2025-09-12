package com.finpay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.route.RouteLocator;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRotes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/auth-services/**")
                        .uri("http://localhost:8081"))
                .route("account-service", r -> r.path("/accounts/**")
                        .uri("http://localhost:8082"))
                .route("transaction-service", r -> r.path("/transactions/**")
                        .filters(f -> f.circuitBreaker(c -> c
                                .setName("transactionCB")
                                .setFallbackUri("forward:/fallback/transactions")))
                        .uri("http://localhost:8083"))
                .route("fraud-service", r -> r.path("/fraud/**")
                        .filters(f -> f
                                .requestRateLimiter(c -> {
                                    c.setRateLimiter(new RedisRateLimiter(5, 10));
                                    // 5 requests per second, burst capacity 10
                                })
                        )
                        .uri("http://localhost:8084")
                )
                .route("notification-service", r -> r.path("/notifications/**")
                        .uri("http://localhost:8085"))
                // Swagger API docs routes
                .route("api-gateway-docs", r -> r.path("/v3/api-docs/gateway")
                        .filters(f -> f.rewritePath("/v3/api-docs/gateway", "/v3/api-docs"))
                        .uri("http://localhost:8080"))
                .route("auth-docs", r -> r.path("/v3/api-docs/auth")
                        .filters(f -> f.rewritePath("/v3/api-docs/auth", "/v3/api-docs"))
                        .uri("http://localhost:8081"))
                .route("account-docs", r -> r.path("/v3/api-docs/account")
                        .filters(f -> f.rewritePath("/v3/api-docs/account", "/v3/api-docs"))
                        .uri("http://localhost:8082"))
                .route("transaction-docs", r -> r.path("/v3/api-docs/transaction")
                        .filters(f -> f.rewritePath("/v3/api-docs/transaction", "/v3/api-docs"))
                        .uri("http://localhost:8083"))
                .build();
    }
}
