package com.finpay.common.dto.transactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for initiating a money transfer between accounts.
 * Requires source account, destination account, and transfer amount.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {
    /** Account ID to debit (source) */
    private UUID fromAccountId;
    /** Account ID to credit (destination) */
    private UUID toAccountId;
    /** Amount to transfer between accounts */
    private BigDecimal amount;
}

