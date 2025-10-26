package com.finpay.common.dto.users;

import com.finpay.common.enums.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for user information.
 * Contains complete user details including personal info, role, and location.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    /** Unique user identifier */
    private Long id;
    /** Username for authentication */
    private String username;
    /** User's email address */
    private String email;
    /** User's first name */
    private String firstName;
    /** User's last name */
    private String lastName;
    /** User's role (ADMIN or USER) */
    private RoleEnum role;
    /** User's location/place name */
    private String location;
}