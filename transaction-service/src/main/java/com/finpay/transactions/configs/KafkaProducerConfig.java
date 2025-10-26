package com.finpay.transactions.configs;

import com.finpay.common.dto.transactions.TransactionCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka producer.
 * Sets up Kafka producer to publish transaction events to Kafka topics.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Creates and configures a Kafka producer factory for TransactionCreatedEvent.
     * Configures:
     * - Bootstrap servers (Kafka broker locations)
     * - Key serializer (String serialization for transaction IDs)
     * - Value serializer (JSON serialization for event objects)
     *
     * @return ProducerFactory configured for publishing TransactionCreatedEvent objects
     */
    @Bean
    public ProducerFactory<String, TransactionCreatedEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Creates a KafkaTemplate for sending TransactionCreatedEvent messages.
     * The template simplifies publishing messages to Kafka topics.
     *
     * @return KafkaTemplate configured with the producer factory
     */
    @Bean
    public KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

