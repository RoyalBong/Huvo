package com.huvo.security;

/**
 * The token is missing, malformed, tampered with, signed by an unknown key, of the wrong type
 * (refresh used as access or vice versa), or lacks required claims. Callers map this to HTTP 401.
 */
public class InvalidTokenException extends HuvoSecurityException {

  public InvalidTokenException(String message) {
    super(message);
  }

  public InvalidTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
