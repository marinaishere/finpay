package com.finpay.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the FinPay Notification Service.
 * This microservice handles sending notifications to users via various channels
 * (EMAIL, SMS, etc.) and consumes fraud detection events from Kafka.
 */
@SpringBootApplication
public class NotificationServiceApplication {
    /**
     * Main entry point for the Notification Service application.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}