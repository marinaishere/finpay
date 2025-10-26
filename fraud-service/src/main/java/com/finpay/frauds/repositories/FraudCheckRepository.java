package com.finpay.frauds.repositories;

import com.finpay.frauds.models.FraudCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for FraudCheck entity data access.
 * Provides CRUD operations and custom query methods for FraudCheck entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface FraudCheckRepository extends JpaRepository<FraudCheck, UUID> {
    /**
     * Finds a fraud check record by transaction ID.
     *
     * @param transactionId The UUID of the transaction
     * @return Optional containing the FraudCheck if found, empty otherwise
     */
    Optional<FraudCheck> findByTransactionId(UUID transactionId);
}

