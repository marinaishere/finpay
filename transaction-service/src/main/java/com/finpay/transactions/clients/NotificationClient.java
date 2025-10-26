package com.finpay.transactions.clients;

import com.finpay.common.dto.notifications.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client for communicating with the Notification Service.
 * <p>
 * This client provides HTTP-based communication with the Notification Service to send
 * notifications to users about transaction outcomes. Notifications can be sent through
 * various channels such as:
 * <ul>
 *   <li>Email notifications</li>
 *   <li>SMS alerts</li>
 *   <li>Push notifications</li>
 *   <li>In-app messages</li>
 * </ul>
 * <p>
 * Used to notify users about:
 * <ul>
 *   <li>Successful transaction completion</li>
 *   <li>Transaction failures and errors</li>
 *   <li>Fraud alerts</li>
 *   <li>Account activity updates</li>
 * </ul>
 * <p>
 * <b>Service Communication:</b> Connects to Notification Service at http://localhost:8084/notifications
 * <p>
 * <b>Note:</b> This client uses default Feign configuration without JWT forwarding,
 * as the notification service may have different authentication requirements.
 *
 * @author FinPay Team
 * @version 1.0
 * @since 1.0
 */
@FeignClient(name = "notification-service", url = "http://localhost:8084/notifications")
public interface NotificationClient {

    /**
     * Sends a notification to a user through the specified channel.
     * <p>
     * This is a fire-and-forget operation that sends the notification asynchronously.
     * The notification service handles delivery through the appropriate channel
     * (email, SMS, etc.) based on the request configuration.
     * <p>
     * Common use cases:
     * <ul>
     *   <li>Notify user of successful transaction: "Transaction Completed Successfully"</li>
     *   <li>Alert user of failed transaction: "Transaction failed. Please try again."</li>
     * </ul>
     *
     * @param request the notification request containing user ID, message, and channel
     */
    @PostMapping
    void sendNotification(NotificationRequest request);
}

