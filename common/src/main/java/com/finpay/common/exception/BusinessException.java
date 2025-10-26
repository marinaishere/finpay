package com.finpay.common.exception;

/**
 * Custom runtime exception for business logic errors.
 * Used across all microservices to represent business rule violations
 * or domain-specific error conditions.
 */
public class BusinessException extends RuntimeException {
    /**
     * Constructs a new BusinessException with the specified detail message.
     *
     * @param message The detail message explaining the business error
     */
    public BusinessException(String message) {
        super(message);
    }
}
