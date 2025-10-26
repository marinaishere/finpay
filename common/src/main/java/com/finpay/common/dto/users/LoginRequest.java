package com.finpay.common.dto.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login/authentication.
 * Contains credentials required for authentication.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    /** Username for authentication */
    private String username;
    /** User's password */
    private String password;
}
