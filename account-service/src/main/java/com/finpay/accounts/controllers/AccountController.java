package com.finpay.accounts.controllers;

import com.finpay.accounts.services.AccountService;
import com.finpay.common.dto.accounts.AccountDto;
import com.finpay.common.dto.accounts.CreateAccountRequest;
import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * REST controller for account management operations.
 * Handles account creation, balance queries, and debit/credit transactions.
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    /**
     * Constructs the AccountController with required dependencies.
     *
     * @param service Service handling account business logic
     */
    public AccountController(AccountService service) {
        this.service = service;
    }

    /**
     * Retrieves authenticated user information from JWT token.
     * Extracts user ID, email, and roles from the JWT claims.
     *
     * @param jwt JWT token of the authenticated user
     * @return Map containing user ID, email, and roles
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
     * Creates a new account.
     *
     * @param request CreateAccountRequest containing owner email and initial balance
     * @return ResponseEntity with created AccountDto
     */
    @PostMapping
    public ResponseEntity<AccountDto> create(@RequestBody CreateAccountRequest request) {
        AccountDto dto = service.createAccount(request.getOwnerEmail(), request.getInitialBalance());
        return ResponseEntity.ok(dto);
    }

    /**
     * Debits (withdraws) an amount from an account.
     *
     * @param request DebitRequest containing account ID and amount
     * @return ResponseEntity with updated AccountDto
     */
    @PostMapping("/debit")
    public ResponseEntity<AccountDto> debit(@RequestBody DebitRequest request) {
        return ResponseEntity.ok(service.debit(request.getAccountId(), request.getAmount()));
    }

    /**
     * Credits (deposits) an amount to an account.
     *
     * @param request CreditRequest containing account ID and amount
     * @return ResponseEntity with updated AccountDto
     */
    @PostMapping("/credit")
    public ResponseEntity<AccountDto> credit(@RequestBody CreditRequest request) {
        return ResponseEntity.ok(service.credit(request.getAccountId(), request.getAmount()));
    }

    /**
     * Retrieves account details by ID.
     *
     * @param id UUID of the account to retrieve
     * @return ResponseEntity with AccountDto
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getAccount(id));
    }
}
