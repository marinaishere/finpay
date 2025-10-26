package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data Transfer Object for account information.
 * Contains complete account details including unique identifier, owner email, and current balance.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDto {
    /** Unique account identifier (UUID) */
    private UUID id;
    /** Email address of the account owner */
    private String ownerEmail;
    /** Current account balance using BigDecimal for precision */
    private BigDecimal balance;
}
