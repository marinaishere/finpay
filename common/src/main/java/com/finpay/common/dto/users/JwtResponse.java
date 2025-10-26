package com.finpay.common.dto.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO containing JWT authentication token.
 * Returned after successful user login.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtResponse {
    /** JWT bearer token for authentication */
    private String token;
}
