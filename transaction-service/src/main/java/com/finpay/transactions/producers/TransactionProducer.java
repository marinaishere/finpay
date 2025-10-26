package com.finpay.transactions.producers;

import com.finpay.common.dto.transactions.TransactionCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka producer for publishing transaction events to the event stream.
 * <p>
 * This producer publishes {@link TransactionCreatedEvent} messages to the
 * "transactions-topic" Kafka topic whenever a new transaction is initiated.
 * <p>
 * <b>Published Events:</b>
 * <ul>
 *   <li><b>Event Type:</b> TransactionCreatedEvent</li>
 *   <li><b>Topic Name:</b> transactions-topic</li>
 *   <li><b>Key:</b> Transaction ID (UUID as String)</li>
 *   <li><b>Payload:</b> Transaction details including ID, amount, and user email</li>
 * </ul>
 * <p>
 * These events enable event-driven architecture patterns such as:
 * <ul>
 *   <li>Real-time analytics and monitoring</li>
 *   <li>Audit logging and compliance tracking</li>
 *   <li>Asynchronous notification processing</li>
 *   <li>Integration with downstream services</li>
 *   <li>Event sourcing and CQRS patterns</li>
 * </ul>
 * <p>
 * The Kafka template is configured via {@link com.finpay.transactions.configs.KafkaProducerConfig}
 * with JSON serialization for the event payload.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 * @see com.finpay.transactions.configs.KafkaProducerConfig
 * @see TransactionCreatedEvent
 */
@Service
public class TransactionProducer {

    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    /**
     * Constructs a new TransactionProducer with the configured Kafka template.
     *
     * @param kafkaTemplate the Kafka template for sending messages to the transactions-topic
     */
    public TransactionProducer(KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a transaction created event to the Kafka topic.
     * <p>
     * Sends the event to the "transactions-topic" with the transaction ID as the key.
     * Using the transaction ID as the key ensures that all events for the same transaction
     * are sent to the same partition, maintaining ordering guarantees.
     * <p>
     * This is a fire-and-forget operation. For production systems, consider adding
     * callbacks or error handling to manage send failures.
     *
     * @param event the transaction created event containing transaction details
     */
    public void sendTransaction(TransactionCreatedEvent event) {
        kafkaTemplate.send("transactions-topic", event.getId().toString(), event);
    }
}

