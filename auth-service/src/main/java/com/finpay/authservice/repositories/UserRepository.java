package com.finpay.authservice.repositories;

import com.finpay.authservice.models.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository interface for UserEntity data access.
 * Provides CRUD operations and custom query methods for User entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    /**
     * Finds a user by their username.
     * Used primarily for authentication and loading user details.
     *
     * @param username The username to search for
     * @return Optional containing the UserEntity if found, empty otherwise
     */
    Optional<UserEntity> findByUsername(String username);
}
