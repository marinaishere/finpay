package com.finpay.notifications.controllers;

import com.finpay.notifications.models.Notification;
import com.finpay.notifications.models.NotificationRequest;
import com.finpay.notifications.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for notification operations.
 * Handles sending notifications to users via various channels.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /**
     * Creates and sends a notification to a user.
     * Accepts notification details and attempts delivery via the specified channel.
     *
     * @param request NotificationRequest containing userId, message, and channel
     * @return Notification entity with delivery status
     */
    @PostMapping
    public Notification create(@RequestBody NotificationRequest request) {
        return service.sendNotification(request);
    }
}

