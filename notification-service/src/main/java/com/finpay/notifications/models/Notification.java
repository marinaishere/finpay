package com.finpay.notifications.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing a notification sent to a user.
 * Stores notification details and delivery status.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification {
    /**
     * Unique identifier for the notification.
     * Auto-generated using database identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User identifier (typically email) to whom the notification is sent.
     */
    private String userId;

    /**
     * Content/body of the notification message.
     */
    private String message;

    /**
     * Delivery channel for the notification (e.g., EMAIL, SMS, PUSH).
     */
    private String channel;

    /**
     * Current status of the notification (PENDING, SENT, FAILED).
     */
    private String status;
}

