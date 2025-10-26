package com.finpay.common.dto.frauds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event DTO published to Kafka after fraud check completion.
 * Used to notify other services about fraud detection results.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckEvent {
    /** Transaction ID that was checked */
    private UUID transactionId;
    /** Whether the transaction was flagged as fraudulent */
    private boolean fraudulent;
}

