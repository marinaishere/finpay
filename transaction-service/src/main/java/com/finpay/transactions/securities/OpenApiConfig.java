package com.finpay.transactions.securities;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * Configures Swagger UI to support JWT Bearer token authentication.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures OpenAPI documentation with JWT Bearer authentication support.
     * This enables the "Authorize" button in Swagger UI where users can enter their JWT token.
     *
     * The configuration includes:
     * - Bearer authentication security scheme using JWT tokens
     * - API metadata (title, description, version)
     * - Server information for API Gateway
     *
     * @return Configured OpenAPI instance
     */
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                // Add security requirement globally to all endpoints
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // Configure security scheme definition
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Add API metadata
                .info(new Info()
                        .title("Transaction Service API")
                        .description("API documentation for FinPay Transaction Service")
                        .version("1.0"))
                // Configure server information
                .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                        .url("http://localhost:8080")
                        .description("API Gateway"));
    }
}


