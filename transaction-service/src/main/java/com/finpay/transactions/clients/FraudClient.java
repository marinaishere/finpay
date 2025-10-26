package com.finpay.transactions.clients;

import com.finpay.common.dto.accounts.AccountDto;
import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import com.finpay.common.dto.frauds.FraudCheckRequest;
import com.finpay.common.dto.frauds.FraudCheckResponse;
import com.finpay.transactions.configs.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Feign client for communicating with the Fraud Detection Service.
 * <p>
 * This client provides HTTP-based communication with the Fraud Service to perform
 * fraud checks on transactions before they are processed. It helps prevent fraudulent
 * activities such as:
 * <ul>
 *   <li>Unusual transaction patterns</li>
 *   <li>Suspicious account behavior</li>
 *   <li>High-risk transfers</li>
 *   <li>Known fraudulent accounts</li>
 * </ul>
 * <p>
 * The client automatically forwards JWT authentication tokens via the FeignConfig
 * interceptor, ensuring secure service-to-service communication.
 * <p>
 * <b>Service Communication:</b> Connects to Fraud Service at http://localhost:8085/frauds
 * <p>
 * <b>Note:</b> This client is currently injected but not actively used in the transaction
 * processing flow. It's available for future fraud detection integration.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 * @see FeignConfig
 */
@FeignClient(
        name = "fraud-service",
        url = "http://localhost:8085/frauds",
        configuration = FeignConfig.class
)
public interface FraudClient {

    /**
     * Performs a fraud check on a transaction.
     * <p>
     * Analyzes the transaction details (account IDs, amount, patterns) to determine
     * if the transaction is potentially fraudulent. The response indicates whether
     * the transaction should be allowed or blocked.
     *
     * @param request the fraud check request containing transaction details to analyze
     * @return FraudCheckResponse indicating whether the transaction is safe or suspicious
     * @throws feign.FeignException if the fraud service is unavailable
     */
    @PostMapping("/check")
    FraudCheckResponse checkFraud(@RequestBody FraudCheckRequest request);
}

