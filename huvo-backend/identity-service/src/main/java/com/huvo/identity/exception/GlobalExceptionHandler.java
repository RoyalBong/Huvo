package com.huvo.identity.exception;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders every failure as the single documented error body (Huvo_Backend_Context.md Section 10).
 * Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own exceptions (405, 415,
 * unreadable JSON, type mismatch) on their correct status codes while still using this body - a
 * plain {@code @ExceptionHandler(Exception.class)} would turn all of them into 500s.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Object> handleResourceNotFound(
      ResourceNotFoundException ex, WebRequest request) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
  }

  /**
   * Login/refresh failures keep the same generic message for an unknown user, a wrong password and
   * a stale refresh token, so the response never reveals whether a username exists (Section 4.2).
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<Object> handleInvalidCredentials(
      InvalidCredentialsException ex, WebRequest request) {
    return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null);
  }

  @ExceptionHandler(DuplicateUsernameException.class)
  public ResponseEntity<Object> handleDuplicateUsername(
      DuplicateUsernameException ex, WebRequest request) {
    return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, null);
  }

  /**
   * Denials raised by {@code @PreAuthorize} surface here, because method-security exceptions are
   * thrown inside the handler and never reach the security filter chain (whose own 403 renderer in
   * {@link com.huvo.identity.config.SecurityConfig} covers URL-rule denials). The message stays
   * generic - the rule expression is not the caller's business.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
    return build(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "Your role does not permit this operation",
        request,
        null);
  }

  /** Guard clauses such as an unknown Access Role on user creation. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Object> handleIllegalArgument(
      IllegalArgumentException ex, WebRequest request) {
    return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
    // Log the detail server-side; never leak it to the caller.
    log.error("Unhandled exception while serving {}", path(request), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred",
        request,
        null);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, details);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    String error = statusCode instanceof HttpStatus httpStatus ? httpStatus.name() : "ERROR";
    return build(statusCode, error, ex.getMessage(), request, null);
  }

  private ResponseEntity<Object> build(
      HttpStatusCode status,
      String error,
      String message,
      WebRequest request,
      List<String> details) {
    String path = path(request);
    ErrorResponse body =
        details == null
            ? ErrorResponse.of(status.value(), error, message, path)
            : ErrorResponse.of(status.value(), error, message, path, details);
    return ResponseEntity.status(status).body(body);
  }

  private String path(WebRequest request) {
    return request instanceof ServletWebRequest servletWebRequest
        ? servletWebRequest.getRequest().getRequestURI()
        : request.getDescription(false);
  }
}
