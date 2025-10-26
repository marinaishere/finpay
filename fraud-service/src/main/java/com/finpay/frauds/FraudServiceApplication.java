package com.finpay.frauds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay Fraud Detection Service.
 * This microservice performs fraud checks on transactions and publishes
 * fraud detection events to Kafka for downstream processing.
 */
@SpringBootApplication
public class FraudServiceApplication {
    /**
     * Main entry point for the Fraud Service application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}