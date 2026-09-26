package com.huvo.security;

/** Base failure for every token problem. Never carries the token itself in the message. */
public class HuvoSecurityException extends RuntimeException {

  public HuvoSecurityException(String message) {
    super(message);
  }

  public HuvoSecurityException(String message, Throwable cause) {
    super(message, cause);
  }
}
