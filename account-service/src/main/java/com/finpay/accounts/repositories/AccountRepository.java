package com.finpay.accounts.repositories;

import com.finpay.accounts.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Account entity data access.
 * Provides CRUD operations and custom query methods for Account entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {
    /**
     * Finds an account by the owner's email address.
     *
     * @param email The email address of the account owner
     * @return Optional containing the Account if found, empty otherwise
     */
    Optional<Account> findByOwnerEmail(String email);
}

