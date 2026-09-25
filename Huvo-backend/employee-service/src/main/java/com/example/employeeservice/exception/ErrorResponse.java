package com.example.employeeservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The single error body shape for this service (Huvo Backend_Context.md §10):
 *
 * <pre>{"timestamp":"...","status":404,"error":"NOT_FOUND","message":"...","path":"..."}</pre>
 *
 * <p>{@code details} is only present for validation failures, where it lists the
 * offending fields. It is omitted (NON_NULL) otherwise so the documented shape is
 * preserved.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path, details);
    }
}
