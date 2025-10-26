package com.finpay.transactions.services;

import com.finpay.common.dto.accounts.AccountDto;
import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import com.finpay.common.dto.frauds.FraudCheckRequest;
import com.finpay.common.dto.frauds.FraudCheckResponse;
import com.finpay.common.dto.notifications.NotificationRequest;
import com.finpay.common.dto.transactions.TransactionCreatedEvent;
import com.finpay.common.dto.transactions.TransactionResponse;
import com.finpay.common.dto.transactions.TransferRequest;
import com.finpay.transactions.clients.AccountClient;
import com.finpay.transactions.clients.FraudClient;
import com.finpay.transactions.clients.NotificationClient;
import com.finpay.transactions.models.Transaction;
import com.finpay.transactions.producers.TransactionProducer;
import com.finpay.transactions.repositories.TransactionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for managing financial transactions in the FinPay system.
 * <p>
 * This service orchestrates the complete transaction lifecycle including:
 * <ul>
 *   <li>Idempotent transaction processing to prevent duplicate transfers</li>
 *   <li>Coordinating debit and credit operations across accounts</li>
 *   <li>Publishing transaction events to Kafka for event-driven processing</li>
 *   <li>Sending notifications to users about transaction status</li>
 *   <li>Handling transaction failures and retries</li>
 * </ul>
 * <p>
 * Key features:
 * <ul>
 *   <li><b>Idempotency:</b> Uses idempotency keys to ensure exactly-once semantics</li>
 *   <li><b>Transactional integrity:</b> Ensures atomic debit/credit operations</li>
 *   <li><b>Event-driven:</b> Publishes events to Kafka for downstream processing</li>
 *   <li><b>Resilient:</b> Handles failures gracefully with proper error handling</li>
 * </ul>
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository repository;
    private final AccountClient accountClient;
    private final NotificationClient notificationClient;
    private final FraudClient fraudClient;
    private final TransactionProducer transactionProducer;

    /**
     * Constructs a new TransactionService with required dependencies.
     * <p>
     * Uses constructor injection for better testability and immutability.
     *
     * @param repository the repository for persisting transactions
     * @param accountClient Feign client for communicating with the Account Service
     * @param notificationClient Feign client for sending notifications to users
     * @param fraudClient Feign client for fraud detection checks
     * @param transactionProducer Kafka producer for publishing transaction events
     */
    public TransactionService(
            TransactionRepository repository,
            AccountClient accountClient,
            NotificationClient notificationClient,
            FraudClient fraudClient,
            TransactionProducer transactionProducer
    ) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.notificationClient = notificationClient;
        this.fraudClient = fraudClient;
        this.transactionProducer = transactionProducer;
    }

    /**
     * Processes a money transfer between two accounts with idempotency support.
     * <p>
     * This method implements the core transaction logic with the following behavior:
     * <ul>
     *   <li>If a transaction with the same idempotency key exists:
     *     <ul>
     *       <li>COMPLETED/PENDING: Returns the existing transaction (idempotent response)</li>
     *       <li>FAILED: Retries the transaction processing</li>
     *     </ul>
     *   </li>
     *   <li>If no existing transaction: Creates a new transaction and processes it</li>
     * </ul>
     * <p>
     * Processing flow:
     * <ol>
     *   <li>Check for existing transaction using idempotency key</li>
     *   <li>Retrieve account details from Account Service</li>
     *   <li>Publish transaction created event to Kafka</li>
     *   <li>Debit the source account</li>
     *   <li>Credit the destination account</li>
     *   <li>Send notification to user</li>
     *   <li>Update transaction status (COMPLETED or FAILED)</li>
     * </ol>
     *
     * @param idempotencyKey unique key to ensure exactly-once processing of the transaction
     * @param request the transfer request containing source account, destination account, and amount
     * @return TransactionResponse containing the transaction details and status
     */
    @Transactional
    public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {
        log.info("Processing transfer request | key={} | from={} | to={} | amount={}",
                idempotencyKey, request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        // Check for existing transaction to implement idempotency
        Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            Transaction tx = existing.get();
            log.info("Found existing transaction | key={} | status={}", idempotencyKey, tx.getStatus());
            return switch (tx.getStatus()) {
                case COMPLETED, PENDING -> {
                    // Return existing transaction for idempotency (safe to retry)
                    log.info("Returning existing transaction | key={} | status={}", idempotencyKey, tx.getStatus());
                    yield toResponse(tx);
                }
                case FAILED -> {
                    // Retry failed transactions to allow recovery from transient errors
                    log.warn("Retrying failed transaction | key={}", idempotencyKey);
                    yield retryPayment(tx, request);
                }
            };
        }

        // Create brand-new transaction
        Transaction newTx = new Transaction();
        newTx.setFromAccountId(request.getFromAccountId());
        newTx.setToAccountId(request.getToAccountId());
        newTx.setAmount(request.getAmount());
        newTx.setIdempotencyKey(idempotencyKey);
        newTx.setStatus(Transaction.Status.PENDING);
        newTx.setCreatedAt(Instant.now());
        repository.save(newTx);

        log.info("Creating new transaction | key={}", idempotencyKey);
        return processAndSave(newTx, request);
    }

    /**
     * Retries a previously failed transaction.
     * <p>
     * Resets the transaction status to PENDING and attempts to process it again.
     * This allows the system to recover from transient failures such as temporary
     * network issues or downstream service unavailability.
     *
     * @param tx the failed transaction to retry
     * @param request the original transfer request
     * @return TransactionResponse with the updated transaction status
     */
    private TransactionResponse retryPayment(Transaction tx, TransferRequest request) {
        // Reset status to allow retry
        tx.setStatus(Transaction.Status.PENDING);
        return processAndSave(tx, request);
    }

    /**
     * Processes the transaction by coordinating with multiple services.
     * <p>
     * This method orchestrates the complete transaction workflow:
     * <ol>
     *   <li>Retrieves account information from Account Service</li>
     *   <li>Publishes transaction created event to Kafka topic</li>
     *   <li>Debits the source account</li>
     *   <li>Credits the destination account</li>
     *   <li>Sends success/failure notification to user</li>
     *   <li>Updates transaction status accordingly</li>
     * </ol>
     * <p>
     * If any step fails (e.g., insufficient funds, service unavailable), the transaction
     * is marked as FAILED and an error notification is sent to the user.
     *
     * @param tx the transaction entity to process
     * @param request the transfer request with source, destination, and amount
     * @return TransactionResponse containing the final transaction state
     */
    @Transactional
    private TransactionResponse processAndSave(Transaction tx, TransferRequest request) {
        // Retrieve account details to get owner email for notifications
        AccountDto accDto = accountClient.getAccount(request.getFromAccountId());

        // Publish event to Kafka for event-driven processing (analytics, audit logs, etc.)
        transactionProducer.sendTransaction(new TransactionCreatedEvent(
                tx.getId(),
                tx.getAmount(),
                accDto.getOwnerEmail()
        ));

        try {
            // Debit source account - will fail if insufficient funds
            log.info("Debiting account={} amount={}", tx.getFromAccountId(), tx.getAmount());
            accountClient.debit(new DebitRequest(tx.getFromAccountId(), tx.getAmount()));

            // Credit destination account
            log.info("Crediting account={} amount={}", tx.getToAccountId(), tx.getAmount());
            accountClient.credit(new CreditRequest(tx.getToAccountId(), tx.getAmount()));

            // Mark transaction as completed
            tx.setStatus(Transaction.Status.COMPLETED);
            log.info("Transaction completed id={} | key={}", tx.getId(), tx.getIdempotencyKey());

            // Send success notification to user
            notificationClient.sendNotification(NotificationRequest.builder()
                    .userId(accDto.getOwnerEmail())
                    .message("Transaction Completed Successfully")
                    .channel("EMAIL")
                    .build()
            );

        } catch (Exception e) {
            // Handle any errors during transaction processing
            tx.setStatus(Transaction.Status.FAILED);
            log.error("Transaction failed id={} | key={} | reason={}",
                    tx.getId(), tx.getIdempotencyKey(), e.getMessage(), e);

            // Send failure notification to user
            notificationClient.sendNotification(NotificationRequest.builder()
                    .userId(accDto.getOwnerEmail())
                    .message("Transaction failed. Please try again.")
                    .channel("EMAIL")
                    .build());
        }

        // Persist final transaction state to database
        Transaction saved = repository.save(tx);
        return toResponse(saved);
    }

    /**
     * Converts a Transaction entity to a TransactionResponse DTO.
     * <p>
     * This helper method transforms the internal entity representation to the
     * response format exposed to clients through the REST API.
     *
     * @param tx the transaction entity to convert
     * @return TransactionResponse DTO containing transaction details
     */
    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getStatus().name()
        );
    }

    /**
     * Retrieves the current status of a transaction by its ID.
     * <p>
     * This method allows clients to check the status of a previously initiated
     * transaction, which is particularly useful for async processing where the
     * client needs to poll for completion.
     *
     * @param id the unique identifier of the transaction
     * @return TransactionResponse containing the transaction details and current status
     * @throws RuntimeException if the transaction with the given ID is not found
     */
    public TransactionResponse getStatus(UUID id) {
        Transaction tx = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return new TransactionResponse(
                tx.getId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getStatus().name()
        );
    }

    /**
     * Retrieves all transactions associated with a specific account.
     * <p>
     * Returns the complete transaction history for an account, including both
     * incoming (credits) and outgoing (debits) transactions. Results are ordered
     * by creation time with the most recent transactions first.
     *
     * @param accountId the unique identifier of the account
     * @return a list of TransactionResponse objects representing the account's transaction history
     */
    public java.util.List<TransactionResponse> getTransactionsByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).stream()
                .map(this::toResponse)
                .toList();
    }
}

