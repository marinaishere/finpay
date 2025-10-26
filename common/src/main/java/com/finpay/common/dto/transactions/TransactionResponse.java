package com.finpay.common.dto.transactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for transaction operations.
 * Contains complete transaction details including status (e.g., "COMPLETED", "FAILED").
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {
    /** Unique transaction identifier */
    private UUID id;
    /** Source account ID (debit) */
    private UUID fromAccountId;
    /** Destination account ID (credit) */
    private UUID toAccountId;
    /** Transaction amount */
    private BigDecimal amount;
    /** Transaction status (e.g., "COMPLETED", "FAILED") */
    private String status;
}

