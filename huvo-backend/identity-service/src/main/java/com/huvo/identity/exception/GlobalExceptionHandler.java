package com.huvo.identity.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Renders every failure as the single documented error body (Huvo_Backend_Context.md
 * Section 10). Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own
 * exceptions (405, 415, unreadable JSON, type mismatch) on their correct status codes
 * while still using this body - a plain {@code @ExceptionHandler(Exception.class)} would
 * turn all of them into 500s.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        // Log the detail server-side; never leak it to the caller.
        log.error("Unhandled exception while serving {}", path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, details);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String error = statusCode instanceof HttpStatus httpStatus ? httpStatus.name() : "ERROR";
        return build(statusCode, error, ex.getMessage(), request, null);
    }

    private ResponseEntity<Object> build(HttpStatusCode status, String error, String message,
                                         WebRequest request, List<String> details) {
        String path = path(request);
        ErrorResponse body = details == null
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
