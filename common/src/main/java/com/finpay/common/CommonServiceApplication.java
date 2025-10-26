package com.finpay.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay Common module.
 * This module contains shared DTOs, enums, exceptions, and utility classes
 * used across all microservices in the FinPay system.
 * It is typically not run as a standalone service but included as a dependency.
 */
@SpringBootApplication
public class CommonServiceApplication {
    /**
     * Main entry point (typically not used as this is a shared library).
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CommonServiceApplication.class, args);
    }
}
