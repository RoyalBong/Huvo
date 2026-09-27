package com.huvo.worklife.exception;

/** A referenced record does not exist. Rendered as 404. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
