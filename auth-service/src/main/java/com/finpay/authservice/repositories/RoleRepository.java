package com.finpay.authservice.repositories;

import com.finpay.authservice.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository interface for Role entity data access.
 * Provides CRUD operations and custom query methods for Role entities.
 * Extends JpaRepository to inherit standard database operations.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    /**
     * Finds a role by its name.
     *
     * @param roleName The name of the role to find (e.g., "USER", "ADMIN")
     * @return Optional containing the Role if found, empty otherwise
     */
    Optional<Role> findByRoleName(String roleName);
}
