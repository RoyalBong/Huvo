package com.huvo.identity.exception;

/**
 * Wrong username, wrong password, unknown or already-rotated refresh token. One message for all of
 * them on purpose (Huvo_Backend_Context.md Section 4.2): the response must never reveal whether the
 * username exists.
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid username or password");
  }
}
