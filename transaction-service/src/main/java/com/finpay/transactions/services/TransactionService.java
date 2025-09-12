package com.finpay.transactions.services;

import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import com.finpay.common.dto.transactions.TransactionResponse;
import com.finpay.common.dto.transactions.TransferRequest;
import com.finpay.transactions.clients.AccountClient;
import com.finpay.transactions.models.Transaction;
import com.finpay.transactions.repositories.TransactionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository repository;
    private final AccountClient accountClient;

    public TransactionService(TransactionRepository repository, AccountClient accountClient) {
        this.repository = repository;
        this.accountClient = accountClient;
    }

    @Transactional
    public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {
        log.info("Processing transfer request | key={} | from={} | to={} | amount={}",
                idempotencyKey, request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            Transaction tx = existing.get();
            log.info("Found existing transaction | key={} | status={}", idempotencyKey, tx.getStatus());
            return switch (tx.getStatus()) {
                case COMPLETED, PENDING -> {
                    log.info("Returning existing transaction | key={} | status={}", idempotencyKey, tx.getStatus());
                    yield toResponse(tx);
                }
                case FAILED -> {
                    log.warn("Retrying failed transaction | key={}", idempotencyKey);
                    yield retryPayment(tx, request);
                }
            };
        }

        // brand new transaction
        Transaction newTx = new Transaction();
        newTx.setFromAccountId(request.getFromAccountId());
        newTx.setToAccountId(request.getToAccountId());
        newTx.setAmount(request.getAmount());
        newTx.setIdempotencyKey(idempotencyKey);
        newTx.setStatus(Transaction.Status.PENDING);

        log.info("Creating new transaction | key={}", idempotencyKey);
        return processAndSave(newTx);
    }

    private TransactionResponse retryPayment(Transaction tx, TransferRequest request) {
        tx.setStatus(Transaction.Status.PENDING); // reset status
        return processAndSave(tx);
    }

    private TransactionResponse processAndSave(Transaction tx) {
        try {
            log.info("Debiting account={} amount={}", tx.getFromAccountId(), tx.getAmount());
            accountClient.debit(new DebitRequest(tx.getFromAccountId(), tx.getAmount()));

            log.info("Crediting account={} amount={}", tx.getToAccountId(), tx.getAmount());
            accountClient.credit(new CreditRequest(tx.getToAccountId(), tx.getAmount()));

            tx.setStatus(Transaction.Status.COMPLETED);
            log.info("Transaction completed | id={} | key={}", tx.getId(), tx.getIdempotencyKey());
        } catch (Exception e) {
            tx.setStatus(Transaction.Status.FAILED);
            log.error("Transaction failed | id={} | key={} | reason={}",
                    tx.getId(), tx.getIdempotencyKey(), e.getMessage(), e);
        }

        Transaction saved = repository.save(tx);
        return toResponse(saved);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getStatus().name()
        );
    }

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
}

