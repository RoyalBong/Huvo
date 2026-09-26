package com.huvo.audit;

/**
 * Raised when an audit entry cannot be written to {@code huvo_audit_log}.
 *
 * <p>Unchecked so it cannot be swallowed by a {@code catch (Exception)} in a service's mutation
 * path. A failed audit write is a real operational problem and the caller decides what to do about
 * it - see {@link AuditClient} for the fail-open discussion.
 */
public class AuditWriteException extends RuntimeException {

  public AuditWriteException(String message, Throwable cause) {
    super(message, cause);
  }
}
