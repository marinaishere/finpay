package com.finpay.authservice.securities;

import static org.springframework.security.config.Customizer.withDefaults;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.finpay.authservice.services.UserService;
import com.finpay.common.utils.PemUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Spring Security configuration for the Auth Service.
 * Configures JWT-based authentication, authorization rules, and security filters.
 */
@Configuration
public class SpringSecurityConfiguration {

    /**
     * Configures the security filter chain for HTTP requests.
     * Sets up:
     * - Stateless session management (no server-side sessions)
     * - Authorization rules for different endpoints
     * - HTTP Basic authentication
     * - OAuth2 resource server with JWT validation
     * - CSRF protection disabled (appropriate for stateless APIs)
     *
     * @param http HttpSecurity object to configure
     * @return Configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Configure stateless session management (JWT-based, no server sessions)
        http.sessionManagement(
                session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS)
        );
        // Configure authorization rules for endpoints
        http.authorizeHttpRequests(
                auth -> auth
                        // Allow public access to user registration endpoint
                        .requestMatchers(HttpMethod.POST, "/auth-services/users").permitAll()
                        // Allow public access to login endpoint
                        .requestMatchers(HttpMethod.POST, "/auth-services/login").permitAll()
                        // Allow public access to Swagger documentation and actuator endpoints
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
        );

        // Enable HTTP Basic authentication as fallback
        http.httpBasic(withDefaults());

        // Disable CSRF protection (not needed for stateless JWT APIs)
        http.csrf(AbstractHttpConfigurer::disable);

        // Configure OAuth2 resource server with JWT token validation
        http.oauth2ResourceServer(oauth2->oauth2.jwt(withDefaults()));

        return http.build();
    }

    /**
     * Configures the authentication manager with DAO-based authentication.
     * Uses the custom UserService for loading user details and BCrypt for password verification.
     *
     * @param userDetailsService Custom UserService that loads user details from database
     * @param passwordEncoder BCrypt password encoder for secure password comparison
     * @return Configured AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(UserService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder); // Essential for password verification
        return new ProviderManager(authenticationProvider);
    }


    /**
     * Creates a JWK (JSON Web Key) source for JWT signing and validation.
     * The JWKSource provides the RSA key to the JWT encoder/decoder.
     *
     * @param rsaKey The RSA key pair used for JWT operations
     * @return JWKSource containing the RSA key
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (selector, context) -> selector.select(jwkSet);
    }

    /**
     * Loads RSA public and private keys from PEM files and creates an RSAKey.
     * The public key is used to verify JWT signatures.
     * The private key is used to sign JWTs.
     *
     * @return RSAKey containing the public/private key pair with a unique key ID
     * @throws Exception if key files cannot be read or parsed
     */
    @Bean
    public RSAKey rsaKey() throws Exception {
        RSAPublicKey publicKey = PemUtils.loadPublicKey("keys/public.pem");
        RSAPrivateKey privateKey = PemUtils.loadPrivateKey("keys/private.pem");

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Creates a JWT decoder using the RSA public key.
     * Used by Spring Security to validate incoming JWT tokens.
     *
     * @param rsaKey The RSA key containing the public key for verification
     * @return Configured JwtDecoder
     * @throws JOSEException if decoder creation fails
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    /**
     * Creates a JWT encoder using the JWK source.
     * Used to generate signed JWT tokens for authenticated users.
     *
     * @param jwkSource The JWK source containing the signing key
     * @return Configured JwtEncoder
     */
    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Provides a BCrypt password encoder for secure password hashing.
     * BCrypt is a strong, adaptive hashing algorithm resistant to brute-force attacks.
     *
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
