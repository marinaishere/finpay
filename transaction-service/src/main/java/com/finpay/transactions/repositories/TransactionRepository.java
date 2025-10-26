package com.finpay.transactions.repositories;


import com.finpay.transactions.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for accessing and managing Transaction entities.
 * <p>
 * Provides custom query methods for:
 * <ul>
 *   <li>Finding transactions by idempotency key (for duplicate detection)</li>
 *   <li>Retrieving transaction history for a specific account (as sender or receiver)</li>
 * </ul>
 * <p>
 * Extends JpaRepository to inherit standard CRUD operations and JPA-specific
 * functionality for the Transaction entity.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Finds a transaction by its idempotency key.
     * <p>
     * This method is critical for implementing idempotency in the transaction processing.
     * When a client retries a request with the same idempotency key, this method allows
     * the system to return the existing transaction instead of creating a duplicate.
     *
     * @param idempotencyKey the unique idempotency key provided by the client
     * @return an Optional containing the transaction if found, or empty if not found
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all transactions associated with a specific account.
     * <p>
     * Returns transactions where the specified account is either the sender (fromAccountId)
     * or the receiver (toAccountId). Results are ordered by creation timestamp in descending
     * order (most recent first) to provide a chronological transaction history.
     *
     * @param accountId the UUID of the account to retrieve transactions for
     * @return a list of transactions involving the specified account, ordered by creation time (newest first)
     */
    @Query("SELECT t FROM Transaction t WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountId(@Param("accountId") UUID accountId);
}
