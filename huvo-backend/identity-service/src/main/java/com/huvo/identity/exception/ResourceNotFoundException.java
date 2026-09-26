package com.huvo.identity.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to a 404 with the standard
 * error body by {@link GlobalExceptionHandler}.
 *
 * <p>Service-level (not per domain): exception mapping is cross-cutting infrastructure,
 * not a domain concern - the Section 3.3 boundary rule is about entities and repositories.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
