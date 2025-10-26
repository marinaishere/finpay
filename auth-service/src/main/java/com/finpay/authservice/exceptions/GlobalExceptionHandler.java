package com.finpay.authservice.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

/**
 * Global exception handler for the Auth Service.
 * Catches and handles all unhandled exceptions across the application,
 * providing consistent error responses to API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Handles all uncaught exceptions throughout the application.
     * Returns a standardized error response with HTTP 500 status.
     *
     * @param ex The exception that was thrown
     * @return ResponseEntity containing error details with HTTP 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
