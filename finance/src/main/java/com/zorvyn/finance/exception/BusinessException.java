package com.zorvyn.finance.exception;

/**
 * Thrown when a business rule is violated — e.g. duplicate email,
 * invalid date range, or mismatched category and record type.
 * GlobalExceptionHandler maps this to HTTP 400.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}