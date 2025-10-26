package com.finpay.common.dto.notifications;

import lombok.Builder;
import lombok.Data;

/**
 * Request DTO for sending notifications to users.
 * Supports multiple notification channels (EMAIL, SMS, PUSH).
 */
@Data
@Builder
public class NotificationRequest {
    /** User ID to send notification to */
    private String userId;
    /** Notification message content */
    private String message;
    /** Notification delivery channel (e.g., EMAIL, SMS, PUSH) */
    private String channel;
}

