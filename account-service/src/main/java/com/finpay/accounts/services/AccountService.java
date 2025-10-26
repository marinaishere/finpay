package com.finpay.accounts.services;

import com.finpay.accounts.models.Account;
import com.finpay.accounts.repositories.AccountRepository;
import com.finpay.common.dto.accounts.AccountDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service class handling account-related business logic.
 * Manages account creation and balance modifications (debit/credit operations).
 */
@Service
public class AccountService {
    private final AccountRepository repository;

    /**
     * Constructs the AccountService with required dependencies.
     *
     * @param repository Repository for accessing account data
     */
    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new account for a user.
     * If no initial balance is provided, the account starts with zero balance.
     *
     * @param ownerEmail Email address of the account owner
     * @param initialBalance Initial balance for the account (can be null)
     * @return AccountDto containing the created account details
     */
    @Transactional
    public AccountDto createAccount(String ownerEmail, BigDecimal initialBalance) {
        Account acc = new Account();
        acc.setOwnerEmail(ownerEmail);
        acc.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);

        Account saved = repository.save(acc);

        return new AccountDto(saved.getId(), saved.getOwnerEmail(), saved.getBalance());
    }

    /**
     * Debits (withdraws) an amount from an account.
     * Validates that sufficient balance is available before deducting.
     *
     * @param accountId UUID of the account to debit
     * @param amount Amount to debit from the account
     * @return AccountDto with updated balance
     * @throws EntityNotFoundException if account is not found
     * @throws IllegalArgumentException if insufficient balance
     */
    @Transactional
    public AccountDto debit(UUID accountId, BigDecimal amount) {
        Account acc = repository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        // Check for sufficient balance
        if (acc.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        acc.setBalance(acc.getBalance().subtract(amount));
        repository.save(acc);

        return new AccountDto(acc.getId(), acc.getOwnerEmail(), acc.getBalance());
    }

    /**
     * Credits (deposits) an amount to an account.
     * Adds the specified amount to the account balance.
     *
     * @param accountId UUID of the account to credit
     * @param amount Amount to credit to the account
     * @return AccountDto with updated balance
     * @throws EntityNotFoundException if account is not found
     */
    @Transactional
    public AccountDto credit(UUID accountId, BigDecimal amount) {
        Account acc = repository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        acc.setBalance(acc.getBalance().add(amount));
        repository.save(acc);

        return new AccountDto(acc.getId(), acc.getOwnerEmail(), acc.getBalance());
    }

    /**
     * Retrieves account details by account ID.
     *
     * @param accountId UUID of the account to retrieve
     * @return AccountDto containing account details
     * @throws EntityNotFoundException if account is not found
     */
    public AccountDto getAccount(UUID accountId) {
        Account acc = repository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
        return new AccountDto(acc.getId(), acc.getOwnerEmail(), acc.getBalance());
    }
}

