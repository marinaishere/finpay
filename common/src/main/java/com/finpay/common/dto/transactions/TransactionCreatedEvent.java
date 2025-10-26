package com.finpay.common.dto.transactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event DTO published to Kafka when a transaction is created.
 * Used for asynchronous processing like fraud detection and notifications.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionCreatedEvent {
    /** Unique transaction identifier */
    private UUID id;
    /** Transaction amount */
    private BigDecimal amount;
    /** User ID who initiated the transaction */
    private String userId;
}
