package com.huvo.attendance.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.huvo.audit.AuditWriteException;

/**
 * One error shape for every failure (Huvo_Backend_Context.md Section 10), so a client parses errors
 * the same way whichever service produced them.
 *
 * <p>{@link AccessDeniedException} is handled here because a method-security denial is thrown
 * inside the handler and never reaches the filter chain, whose own 403 renderer in {@code
 * SecurityConfig} only covers URL-rule denials.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException ex, WebRequest req) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, WebRequest req) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", req, details);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, WebRequest req) {
    // The rule expression is not the caller's business.
    return build(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "Your role does not permit this operation", req, null);
  }

  /**
   * An override whose audit write failed (Section 5.2 step 8). Surfaced as 503 rather than a
   * generic 500 because the caller's action is retryable and the real problem is a dependency, and
   * because a silent overwrite is exactly what the rule forbids.
   */
  @ExceptionHandler(AuditWriteException.class)
  public ResponseEntity<ErrorResponse> handleAuditUnavailable(
      AuditWriteException ex, WebRequest req) {
    log.error("Refusing the operation because it could not be audited", ex);
    return build(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AUDIT_UNAVAILABLE",
        "This change could not be recorded in the audit log, so it was not applied. Try again shortly.",
        req,
        null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest req) {
    // Log the detail server-side; never leak it to the caller.
    log.error("Unhandled exception while serving {}", path(req), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred",
        req,
        null);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String error, String message, WebRequest request, List<String> details) {
    return ResponseEntity.status(status)
        .body(ErrorResponse.of(status.value(), error, message, path(request), details));
  }

  private static String path(WebRequest request) {
    String description = request.getDescription(false);
    return description.startsWith("uri=") ? description.substring(4) : description;
  }
}
