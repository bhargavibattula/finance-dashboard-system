package com.zorvyn.finance.exception;

import java.util.UUID;

/**
 * Thrown when a requested entity does not exist.
 * GlobalExceptionHandler maps this to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " not found with id: " + id);
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " not found with " + field + ": " + value);
    }
}