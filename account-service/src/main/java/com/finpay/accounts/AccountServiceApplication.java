package com.finpay.accounts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay Account Service.
 * This microservice manages user account balances and financial transactions
 * including account creation, debit, and credit operations.
 *
 * @SpringBootApplication enables auto-configuration, component scanning, and configuration properties.
 */
@SpringBootApplication
public class AccountServiceApplication {
    /**
     * Main entry point for the Account Service application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}

