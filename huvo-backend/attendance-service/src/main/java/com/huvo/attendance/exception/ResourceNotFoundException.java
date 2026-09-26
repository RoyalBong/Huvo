package com.huvo.attendance.exception;

/** Raised when an attendance record does not exist, rendered as the documented 404 body. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
