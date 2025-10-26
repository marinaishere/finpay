package com.finpay.frauds.services;

import com.finpay.common.dto.frauds.FraudCheckEvent;
import com.finpay.common.dto.transactions.TransactionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Kafka consumer for transaction events.
 * Listens to transaction-topic and performs fraud checks on new transactions.
 */
@Service
public class FraudConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudConsumer.class);
    private final FraudService fraudService;

    /**
     * Constructs the FraudConsumer with required dependencies.
     *
     * @param fraudService Service for performing fraud checks
     */
    public FraudConsumer(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    /**
     * Consumes transaction created events from Kafka.
     * Performs fraud check on each transaction and stores the result.
     *
     * @param event TransactionCreatedEvent containing transaction details
     */
    @KafkaListener(topics = "transactions-topic", groupId = "fraud-service-group")
    public void consume(TransactionCreatedEvent event) {
        log.info("Received transaction from Kafka | id={} | amount={} | user={}",
                event.getId(), event.getAmount(), event.getUserId());

        // Perform preliminary fraud check based on amount threshold
        boolean fraudulent = event.getAmount().compareTo(BigDecimal.valueOf(1000)) > 0;

        FraudCheckEvent fraudEvent = new FraudCheckEvent(event.getId(), fraudulent);
        // Execute fraud detection logic and save result
        fraudService.checkFraud(fraudEvent.getTransactionId(), event.getAmount());
    }
}

