package com.finpay.authservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity class representing a user role in the system.
 * Stores role information (e.g., USER, ADMIN) used for authorization.
 */
@Setter
@Getter
@Entity
@Table(name = "roles")
@AllArgsConstructor
@NoArgsConstructor
public class Role {

    /**
     * Unique identifier for the role.
     * Auto-generated using database identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the role (e.g., "USER", "ADMIN").
     * Used to identify the role and grant permissions.
     */
    private String roleName;
}
