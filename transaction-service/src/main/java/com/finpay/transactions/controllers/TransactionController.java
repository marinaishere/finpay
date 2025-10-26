package com.finpay.transactions.controllers;

import com.finpay.common.dto.transactions.TransactionResponse;
import com.finpay.common.dto.transactions.TransferRequest;
import com.finpay.transactions.services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * REST controller for managing transaction operations.
 * <p>
 * This controller exposes HTTP endpoints for:
 * <ul>
 *   <li>Processing money transfers with idempotency support</li>
 *   <li>Retrieving transaction status by ID</li>
 *   <li>Getting transaction history for an account</li>
 *   <li>Accessing authenticated user information</li>
 * </ul>
 * <p>
 * All endpoints (except /me) require JWT authentication. The idempotency mechanism
 * ensures that duplicate transfer requests with the same Idempotency-Key header
 * are handled correctly, preventing duplicate transactions.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    /**
     * Constructs a new TransactionController with the required service dependency.
     *
     * @param service the transaction service for business logic
     */
    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Retrieves the authenticated user's information from the JWT token.
     * <p>
     * Extracts and returns claims from the JWT including user ID, email, and roles.
     * This endpoint is useful for debugging authentication and verifying token contents.
     *
     * @param jwt the JWT token from the authenticated request
     * @return a map containing user_id, email, and roles extracted from the JWT
     */
    @GetMapping("/me")
    public Map<String, Object> myAccount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "user_id", jwt.getClaim("user_id"),
                "email", jwt.getClaim("email"),
                "roles", jwt.getClaim("scope")
        );
    }

    /**
     * Processes a money transfer between two accounts.
     * <p>
     * This endpoint initiates a transfer with idempotency support. The Idempotency-Key
     * header ensures that retrying the same request will not create duplicate transactions.
     * <p>
     * Behavior:
     * <ul>
     *   <li>First request: Creates and processes the transaction</li>
     *   <li>Retry with same key: Returns the existing transaction (COMPLETED/PENDING) or retries (FAILED)</li>
     * </ul>
     * <p>
     * Returns HTTP 202 (Accepted) to indicate async processing, though the transaction
     * is typically completed synchronously.
     *
     * @param key unique idempotency key to prevent duplicate transactions (required header)
     * @param request the transfer request containing fromAccountId, toAccountId, and amount
     * @return ResponseEntity with HTTP 202 and the transaction response
     */
    @Operation(summary = "Transfer money between accounts", description = "Requires valid JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Transaction accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody TransferRequest request
    ) {
        TransactionResponse tx = service.transfer(key, request);
        return ResponseEntity.accepted().body(tx);
    }

    /**
     * Retrieves the status of a specific transaction by ID.
     * <p>
     * This endpoint allows clients to check the current status of a transaction,
     * which is useful for polling after an async transfer request.
     *
     * @param id the unique identifier of the transaction
     * @return TransactionResponse containing the transaction details and status
     */
    @GetMapping("/{id}")
    public TransactionResponse status(@PathVariable UUID id) {
        return service.getStatus(id);
    }

    /**
     * Retrieves the transaction history for a specific account.
     * <p>
     * Returns all transactions where the account is either the sender or receiver,
     * ordered by creation time (most recent first). This provides a complete
     * transaction history for the account.
     *
     * @param accountId the unique identifier of the account
     * @return ResponseEntity containing a list of transactions for the account
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAccount(@PathVariable UUID accountId) {
        List<TransactionResponse> transactions = service.getTransactionsByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }
}
