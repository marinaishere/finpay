package com.finpay.common.dto.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new user account.
 * Contains all required information for user registration.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserRequest {
    /** User's first name */
    private String firstName;
    /** User's last name */
    private String lastName;
    /** User's email address */
    private String email;
    /** Desired username for authentication */
    private String username;
    /** User's password (will be encrypted) */
    private String password;
    /** User's role (e.g., "USER" or "ADMIN") */
    private String role;
    /** User's location/place name */
    private String location;
}
