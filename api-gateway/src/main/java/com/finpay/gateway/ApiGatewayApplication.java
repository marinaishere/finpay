package com.finpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay API Gateway.
 * This gateway routes requests to various microservices including auth, account,
 * transaction, notification, and fraud services. Provides rate limiting,
 * circuit breaker patterns, and aggregates Swagger documentation from all services.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    /**
     * Main entry point for the API Gateway application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
