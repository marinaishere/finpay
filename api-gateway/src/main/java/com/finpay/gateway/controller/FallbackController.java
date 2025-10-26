package com.finpay.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback controller for handling circuit breaker failures.
 * Provides fallback responses when downstream services are unavailable.
 */
@RestController
public class FallbackController {
    /**
     * Fallback endpoint for Transaction Service failures.
     * Returns a user-friendly message when the transaction service is down
     * or not responding due to circuit breaker activation.
     *
     * @return ResponseEntity with fallback message
     */
    @RequestMapping("/fallback/transactions")
    public ResponseEntity<String> transactionFallback() {
        return ResponseEntity.ok("Transaction Service is currently unavailable. Please try again later.");
    }
}
