package com.finpay.authservice.repositories;

import com.finpay.authservice.models.Location;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Location entity data access.
 * Provides CRUD operations and query methods for Location entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface LocationRepository extends JpaRepository<Location, Long> {
}
