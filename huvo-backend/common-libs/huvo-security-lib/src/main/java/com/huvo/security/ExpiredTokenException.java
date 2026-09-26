package com.huvo.security;

/** The token is expired. Callers map this to HTTP 401 so the client refreshes and retries. */
public class ExpiredTokenException extends HuvoSecurityException {

  public ExpiredTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
