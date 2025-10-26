package com.finpay.authservice.services;

import com.finpay.authservice.models.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Custom implementation of Spring Security's UserDetails interface.
 * Wraps the UserEntity to provide authentication and authorization information
 * required by Spring Security.
 */
public class CustomUserDetails implements UserDetails {
    private final UserEntity user;

    /**
     * Constructs a CustomUserDetails wrapper around a UserEntity.
     *
     * @param user The UserEntity containing user information from the database
     */
    public CustomUserDetails(UserEntity user) {
        this.user = user;
    }

    /**
     * Gets the user's unique identifier.
     *
     * @return User ID
     */
    public Long getId() {
        return user.getId();
    }

    /**
     * Gets the user's email address.
     *
     * @return User's email
     */
    public String getEmail() {
        return user.getEmail();
    }

    /**
     * Returns the authorities granted to the user.
     * Converts the user's role to a Spring Security GrantedAuthority with "ROLE_" prefix.
     *
     * @return Collection of granted authorities (user's role)
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleName()));
    }

    /**
     * Returns the password used to authenticate the user.
     *
     * @return User's hashed password
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Returns the username used to authenticate the user.
     *
     * @return User's username
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Indicates whether the user's account has expired.
     * Currently always returns true (accounts don't expire).
     *
     * @return true if the account is non-expired
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user's account is locked.
     * Currently always returns true (accounts are never locked).
     *
     * @return true if the account is non-locked
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indicates whether the user's credentials (password) have expired.
     * Currently always returns true (credentials don't expire).
     *
     * @return true if the credentials are non-expired
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is enabled or disabled.
     * Currently always returns true (all users are enabled).
     *
     * @return true if the user is enabled
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
