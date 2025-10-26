package com.finpay.transactions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Main entry point for the FinPay Transaction Service.
 * <p>
 * This microservice handles financial transactions between accounts, including:
 * <ul>
 *   <li>Processing money transfers with idempotency support</li>
 *   <li>Coordinating debit and credit operations across accounts</li>
 *   <li>Publishing transaction events to Kafka</li>
 *   <li>Integrating with fraud detection and notification services</li>
 * </ul>
 * <p>
 * Feign clients are enabled to communicate with:
 * <ul>
 *   <li>Account Service - for debit/credit operations</li>
 *   <li>Fraud Service - for fraud detection checks</li>
 *   <li>Notification Service - for user notifications</li>
 * </ul>
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.finpay.transactions.clients")
public class TransactionServiceApplication {

    /**
     * Main method to start the Transaction Service application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
