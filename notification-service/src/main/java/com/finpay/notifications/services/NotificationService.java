package com.finpay.notifications.services;

import com.finpay.notifications.models.Notification;
import com.finpay.notifications.models.NotificationRequest;
import com.finpay.notifications.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service class handling notification sending logic.
 * Supports multiple notification channels including EMAIL, SMS, and PUSH notifications.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final JavaMailSender mailSender;

    /**
     * Sends a notification to a user via the specified channel.
     * Creates a notification record with PENDING status, attempts delivery,
     * and updates the status to SENT or FAILED based on the outcome.
     *
     * @param request NotificationRequest containing userId, message, and channel
     * @return Notification entity with delivery status
     */
    public Notification sendNotification(NotificationRequest request) {
        // Create notification record with PENDING status
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .message(request.getMessage())
                .channel(request.getChannel())
                .status("PENDING")
                .build();

        try {
            // Send via EMAIL channel using JavaMailSender
            if ("EMAIL".equalsIgnoreCase(request.getChannel())) {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom("noreply@finpay.com");
                mail.setTo(request.getUserId()); // userId is assumed to be email address
                mail.setSubject("FinPay Notification");
                mail.setText(request.getMessage());
                mailSender.send(mail);
            } else {
                // For other channels (SMS, PUSH), simulate the notification
                log.info("Simulating {} notification for {}: {}", request.getChannel(), request.getUserId(), request.getMessage());
            }

            notification.setStatus("SENT");
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            notification.setStatus("FAILED");
        }

        return repository.save(notification);
    }
}

