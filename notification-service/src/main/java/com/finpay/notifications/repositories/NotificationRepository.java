package com.finpay.notifications.repositories;

import com.finpay.notifications.models.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Notification entity data access.
 * Provides CRUD operations for Notification entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}

