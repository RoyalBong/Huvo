package com.example.departmentservice.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to a 404 with the
 * standard error body by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
