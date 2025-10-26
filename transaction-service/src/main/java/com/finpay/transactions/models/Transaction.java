package com.finpay.transactions.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a financial transaction in the FinPay system.
 * <p>
 * This entity models money transfers between accounts with support for:
 * <ul>
 *   <li>Idempotency to prevent duplicate transactions</li>
 *   <li>Transaction status tracking (PENDING, COMPLETED, FAILED)</li>
 *   <li>Automatic timestamp generation on creation</li>
 *   <li>Bi-directional transaction history (from/to accounts)</li>
 * </ul>
 * <p>
 * The idempotency key ensures that retrying a transaction with the same key
 * will return the existing transaction instead of creating a duplicate.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Entity
@Table(name = "transactions")
public class Transaction {

    /**
     * Unique identifier for the transaction.
     * Auto-generated UUID serves as the primary key.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Account ID from which money is being debited.
     * References the source account in the Account Service.
     */
    private UUID fromAccountId;

    /**
     * Account ID to which money is being credited.
     * References the destination account in the Account Service.
     */
    private UUID toAccountId;

    /**
     * Amount of money being transferred.
     * Uses BigDecimal for precise decimal arithmetic in financial calculations.
     */
    private BigDecimal amount;

    /**
     * Current status of the transaction.
     * Tracks the lifecycle state: PENDING, COMPLETED, or FAILED.
     */
    @Enumerated(EnumType.STRING)
    private Status status;

    /**
     * Unique key provided by the client to ensure idempotency.
     * <p>
     * Multiple requests with the same idempotency key will return the same transaction
     * instead of creating duplicates. This is critical for handling network retries and
     * ensuring exactly-once semantics in distributed systems.
     */
    @Column(unique = true)
    private String idempotencyKey;

    /**
     * Timestamp when the transaction was created.
     * Automatically populated by Hibernate on entity creation.
     */
    @CreationTimestamp
    private Instant createdAt;

    /**
     * Enumeration representing the lifecycle status of a transaction.
     * <ul>
     *   <li>PENDING - Transaction has been initiated but not yet completed</li>
     *   <li>COMPLETED - Transaction successfully processed (debited and credited)</li>
     *   <li>FAILED - Transaction failed due to insufficient funds or other errors</li>
     * </ul>
     */
    public enum Status {
        /** Transaction initiated but not yet processed */
        PENDING,
        /** Transaction successfully completed */
        COMPLETED,
        /** Transaction failed and cannot be completed */
        FAILED
    }
}

