package com.finpay.transactions.configs;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Configuration class for Feign HTTP clients.
 * Configures interceptors to forward authentication tokens to downstream services.
 */
@Configuration
public class FeignConfig {

    /**
     * Creates a request interceptor that forwards JWT authentication tokens.
     * Extracts the JWT token from the current security context and adds it
     * to the Authorization header of outgoing Feign requests.
     * This ensures that inter-service calls maintain the user's authentication context.
     *
     * @return RequestInterceptor that adds Authorization header to Feign requests
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return (RequestTemplate template) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // Forward the Authorization header with the Bearer token to downstream services
                template.header("Authorization", "Bearer " + jwt.getTokenValue());
            }
        };
    }
}

