package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for crediting (adding funds to) an account.
 * Used for deposit operations and incoming transfers.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreditRequest {
    /** Unique identifier of the account to credit */
    private UUID accountId;
    /** Amount to add to the account balance */
    private BigDecimal amount;
}
