package com.finpay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configuration class for rate limiting in the API Gateway.
 * Uses Redis-based rate limiting to control request rates per user.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Creates a Redis-based rate limiter bean.
     * Configures rate limiting to 1 request per second with a burst capacity of 10.
     * This prevents API abuse and ensures fair resource allocation across users.
     *
     * @return RedisRateLimiter configured with replenish rate and burst capacity
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // Allow 1 request per second with burst capacity of 10
        return new RedisRateLimiter(1, 10);
    }

    /**
     * Creates a key resolver for rate limiting based on user authentication.
     * Extracts the JWT token from the Authorization header to identify users.
     * Anonymous users share a common rate limit.
     *
     * @return KeyResolver that identifies users by their JWT token
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Use JWT token as the rate limiting key for authenticated users
                return Mono.just(authHeader.substring(7));
            }
            // All anonymous requests share the same rate limit
            return Mono.just("anonymous");
        };
    }
}


