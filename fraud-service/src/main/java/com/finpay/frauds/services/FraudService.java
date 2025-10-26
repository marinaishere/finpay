package com.finpay.frauds.services;

import com.finpay.common.dto.frauds.FraudCheckResponse;
import com.finpay.frauds.models.FraudCheck;
import com.finpay.frauds.repositories.FraudCheckRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service class handling fraud detection logic.
 * Applies fraud detection rules and stores fraud check results.
 */
@Service
public class FraudService {

    private final FraudCheckRepository repository;

    /**
     * Constructs the FraudService with required dependencies.
     *
     * @param repository Repository for accessing fraud check data
     */
    public FraudService(FraudCheckRepository repository) {
        this.repository = repository;
    }

    /**
     * Performs fraud check on a transaction.
     * Applies business rules to determine if a transaction is fraudulent.
     * Current rule: transactions over 10,000 are flagged as fraudulent.
     *
     * @param transactionId UUID of the transaction to check
     * @param amount Transaction amount
     * @return FraudCheckResponse with fraud determination result
     */
    public FraudCheckResponse checkFraud(UUID transactionId, BigDecimal amount) {
        // Apply fraud detection rule: flag if amount exceeds threshold
        boolean fraudulent = amount.compareTo(BigDecimal.valueOf(10000)) > 0;
        String reason = fraudulent ? "Amount exceeds fraud threshold" : "Transaction is valid";

        // Save fraud check result
        FraudCheck check = new FraudCheck();
        check.setTransactionId(transactionId);
        check.setFraudulent(fraudulent);
        check.setReason(reason);

        repository.save(check);

        return FraudCheckResponse.builder()
                .transactionId(transactionId)
                .fraudulent(fraudulent)
                .reason(reason)
                .build();
    }

    /**
     * Retrieves the fraud check status for a specific transaction.
     *
     * @param transactionId UUID of the transaction
     * @return FraudCheckResponse with fraud check details
     * @throws EntityNotFoundException if fraud check record is not found
     */
    public FraudCheckResponse getFraudStatus(UUID transactionId) {
        FraudCheck fc = repository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("FraudCheck not found"));;

        return FraudCheckResponse.builder()
                .transactionId(fc.getTransactionId())
                .fraudulent(fc.isFraudulent())
                .reason(fc.getReason())
                .build();
    }
}

