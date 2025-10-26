package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for debiting (removing funds from) an account.
 * Used for withdrawal operations and outgoing transfers.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DebitRequest {
    /** Unique identifier of the account to debit */
    private UUID accountId;
    /** Amount to deduct from the account balance */
    private BigDecimal amount;
}
