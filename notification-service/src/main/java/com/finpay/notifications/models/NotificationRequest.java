package com.finpay.notifications.models;

import lombok.*;

/**
 * DTO class for notification requests.
 * Contains the required information to send a notification to a user.
 */
@Data
@NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRequest {
    /** User identifier (typically email address) */
    private String userId;

    /** Notification message content */
    private String message;

    /** Delivery channel (EMAIL, SMS, PUSH, etc.) */
    private String channel;
}

