package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new account.
 * Contains owner email and initial balance for account setup.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAccountRequest {
    /** Email address of the account owner */
    private String ownerEmail;
    /** Initial deposit amount for the new account */
    private BigDecimal initialBalance;
}

