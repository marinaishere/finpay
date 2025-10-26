package com.finpay.frauds.controllers;

import com.finpay.common.dto.frauds.FraudCheckRequest;
import com.finpay.common.dto.frauds.FraudCheckResponse;
import com.finpay.frauds.services.FraudService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for fraud detection operations.
 * Handles fraud check requests and fraud status queries.
 */
@RestController
@RequestMapping("/frauds")
public class FraudController {

    private final FraudService fraudService;

    /**
     * Constructs the FraudController with required dependencies.
     *
     * @param fraudService Service handling fraud detection logic
     */
    public FraudController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    /**
     * Performs a fraud check on a transaction.
     * Analyzes transaction details and returns fraud determination.
     *
     * @param request FraudCheckRequest containing transaction ID and amount
     * @return FraudCheckResponse with fraud check result
     */
    @PostMapping("/check")
    public FraudCheckResponse checkFraud(@RequestBody FraudCheckRequest request) {
        return fraudService.checkFraud(request.getTransactionId(), request.getAmount());
    }

    /**
     * Retrieves the fraud check status for a specific transaction.
     *
     * @param transactionId UUID of the transaction
     * @return FraudCheckResponse with fraud check details
     */
    @GetMapping("/transactions/{transactionId}")
    public FraudCheckResponse getFraudStatus(@PathVariable("transactionId") UUID transactionId) {
        return fraudService.getFraudStatus(transactionId);
    }
}


