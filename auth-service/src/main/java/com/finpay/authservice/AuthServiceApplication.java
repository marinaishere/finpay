package com.finpay.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay Authentication Service.
 * This microservice handles user authentication, authorization, and JWT token management.
 *
 * @SpringBootApplication enables auto-configuration, component scanning, and configuration properties.
 */
@SpringBootApplication
public class AuthServiceApplication {
    /**
     * Main entry point for the Auth Service application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
