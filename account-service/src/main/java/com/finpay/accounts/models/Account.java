package com.finpay.accounts.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

/**
 * Entity class representing a financial account in the system.
 * Each account is associated with a user (via email) and maintains a balance.
 */
@Data
@Entity
@Table(name = "accounts")
public class Account {

    /**
     * Unique identifier for the account.
     * Auto-generated UUID.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Email address of the account owner.
     * Must be unique and cannot be null.
     */
    @Column(nullable = false, unique = true)
    private String ownerEmail;

    /**
     * Current account balance.
     * Stored with precision of 19 digits and 2 decimal places (e.g., 99999999999999999.99).
     * Cannot be null.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
}
