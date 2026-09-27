package com.huvo.worklife.exception;

/**
 * A request that is well-formed but breaks a business rule (Huvo_Backend_Context.md Section 10).
 *
 * <p>Distinct from a validation failure, which means the payload was malformed, and from a security
 * failure. This one means "the system understood you and is declining" - approving leave that has
 * already been approved, assigning outside a department.
 */
public class BusinessRuleException extends RuntimeException {

  public BusinessRuleException(String message) {
    super(message);
  }
}
