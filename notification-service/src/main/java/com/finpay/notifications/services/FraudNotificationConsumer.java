package com.finpay.notifications.services;

import com.finpay.common.dto.frauds.FraudCheckEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for fraud detection events.
 * Listens to fraud-check topic and sends alerts when fraudulent transactions are detected.
 */
@Service
public class FraudNotificationConsumer {

    /**
     * Consumes fraud check events from Kafka.
     * When a fraudulent transaction is detected, sends an alert notification.
     *
     * @param event FraudCheckEvent containing transaction ID and fraud status
     */
    @KafkaListener(topics = "fraud-check", groupId = "notification-service-group")
    public void consume(FraudCheckEvent event) {
        if (event.isFraudulent()) {
            // Send fraud alert notification to relevant parties
            System.out.println("🚨 Send fraud alert for tx: " + event.getTransactionId());
        }
    }
}

