package com.huvo.attendance.exception;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error body every failure returns (Huvo_Backend_Context.md Section 10).
 *
 * @param timestamp when the error was rendered
 * @param status the HTTP status code, repeated in the body for clients that only read the payload
 * @param error a stable machine-readable code, e.g. {@code NOT_FOUND}
 * @param message a human-readable explanation
 * @param path the request path, so a client can correlate a failure with its call
 * @param details per-field validation messages, empty for non-validation failures
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details) {

  public static ErrorResponse of(
      int status, String error, String message, String path, List<String> details) {
    return new ErrorResponse(
        OffsetDateTime.now(), status, error, message, path, details == null ? List.of() : details);
  }
}
