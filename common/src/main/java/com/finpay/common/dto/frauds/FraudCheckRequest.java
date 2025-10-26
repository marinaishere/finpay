package com.finpay.common.dto.frauds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for fraud detection checks.
 * Sent to the fraud service to validate transaction legitimacy.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckRequest {
    /** Transaction ID to check for fraud */
    private UUID transactionId;
    /** Transaction amount (used for threshold validation) */
    private BigDecimal amount;
}

