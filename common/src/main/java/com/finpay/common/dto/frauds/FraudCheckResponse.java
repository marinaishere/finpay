package com.finpay.common.dto.frauds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for fraud detection checks.
 * Contains fraud detection results and reason if flagged.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FraudCheckResponse {
    /** Transaction ID that was checked */
    private UUID transactionId;
    /** Whether the transaction was flagged as fraudulent */
    private boolean fraudulent;
    /** Reason for fraud detection (e.g., "Amount exceeds threshold") */
    private String reason;
}

