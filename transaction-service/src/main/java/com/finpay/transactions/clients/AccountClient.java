package com.finpay.transactions.clients;

import com.finpay.common.dto.accounts.AccountDto;
import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import com.finpay.transactions.configs.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Feign client for communicating with the Account Service.
 * <p>
 * This client provides HTTP-based communication with the Account Service to:
 * <ul>
 *   <li>Debit (withdraw) funds from an account</li>
 *   <li>Credit (deposit) funds to an account</li>
 *   <li>Retrieve account details</li>
 * </ul>
 * <p>
 * The client automatically forwards JWT authentication tokens via the FeignConfig
 * interceptor, ensuring secure service-to-service communication.
 * <p>
 * <b>Service Communication:</b> Connects to Account Service at http://localhost:8082/accounts
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 * @see FeignConfig
 */
@FeignClient(
        name = "account-service",
        url = "http://localhost:8082/accounts",
        configuration = FeignConfig.class
)
public interface AccountClient {

    /**
     * Debits (withdraws) funds from a specified account.
     * <p>
     * This operation reduces the account balance by the specified amount.
     * Will fail if the account has insufficient funds.
     *
     * @param request the debit request containing accountId and amount to withdraw
     * @return AccountDto with the updated account information
     * @throws feign.FeignException if the account doesn't exist or has insufficient funds
     */
    @PostMapping("/debit")
    AccountDto debit(DebitRequest request);

    /**
     * Credits (deposits) funds to a specified account.
     * <p>
     * This operation increases the account balance by the specified amount.
     *
     * @param request the credit request containing accountId and amount to deposit
     * @return AccountDto with the updated account information
     * @throws feign.FeignException if the account doesn't exist
     */
    @PostMapping("/credit")
    AccountDto credit(CreditRequest request);

    /**
     * Retrieves account details by account ID.
     * <p>
     * Fetches account information including owner details, balance, and other metadata.
     * Used primarily to get the account owner's email for notifications.
     *
     * @param id the unique identifier of the account
     * @return AccountDto containing the account details
     * @throws feign.FeignException if the account is not found
     */
    @GetMapping("/{id}")
    public AccountDto getAccount(@PathVariable("id") UUID id);
}

