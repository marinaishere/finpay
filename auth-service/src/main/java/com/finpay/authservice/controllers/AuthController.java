package com.finpay.authservice.controllers;

import com.finpay.authservice.services.CustomUserDetails;
import com.finpay.common.dto.users.JwtResponse;
import com.finpay.common.dto.users.LoginRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * REST controller for authentication operations.
 * Handles user login and JWT token generation.
 */
@RestController
@RequestMapping("/auth-services")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    /**
     * Constructs the AuthController with required dependencies.
     *
     * @param jwtEncoder JWT encoder for generating tokens
     * @param authenticationManager Authentication manager for validating credentials
     */
    public AuthController(JwtEncoder jwtEncoder, AuthenticationManager authenticationManager) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Authenticates a user and generates a JWT token.
     * Validates username and password, then returns a signed JWT token
     * that can be used for subsequent authenticated requests.
     *
     * @param loginRequest LoginRequest containing username and password
     * @return JwtResponse containing the generated JWT token
     */
    @PostMapping("/login")
    public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
        // Authenticate user credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // Generate and return JWT token
        return new JwtResponse(createToken(authentication));
    }

    /**
     * Creates a JWT token for an authenticated user.
     * The token includes user information (ID, email) and expires after 30 minutes.
     *
     * @param authentication Authentication object containing user details
     * @return Signed JWT token string
     */
    private String createToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        // Build JWT claims with user information and expiration
        var claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60 * 30)) // Token expires in 30 minutes
                .subject(authentication.getName())
                .claim("user_id", user.getId())
                .claim("email", user.getEmail())
                .claim("scope", createScope(authentication)) // User roles/authorities
                .build();

        // Encode and return the JWT token
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Extracts user authorities (roles) and formats them as a space-separated string.
     * This scope is included in the JWT token for authorization purposes.
     *
     * @param authentication Authentication object containing user authorities
     * @return Space-separated string of authorities (e.g., "ROLE_USER ROLE_ADMIN")
     */
    private String createScope(Authentication authentication) {
        return authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));
    }
}
