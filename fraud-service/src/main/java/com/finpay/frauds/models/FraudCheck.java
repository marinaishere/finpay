package com.finpay.frauds.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity class representing a fraud check result for a transaction.
 * Stores the outcome of fraud detection analysis.
 */
@Data
@Entity
@Table(name = "fraud_checks")
public class FraudCheck {
    /**
     * Unique identifier for the fraud check record.
     * Auto-generated UUID.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * ID of the transaction that was checked for fraud.
     */
    private UUID transactionId;

    /**
     * Whether the transaction was flagged as fraudulent.
     */
    private boolean fraudulent;

    /**
     * Reason or explanation for the fraud determination.
     */
    private String reason;

    /**
     * Timestamp when the fraud check was performed.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    private Instant createdAt;
}

