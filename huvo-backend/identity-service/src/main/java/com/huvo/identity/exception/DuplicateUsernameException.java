package com.huvo.identity.exception;

/** Two logins cannot share a username; rendered as HTTP 409 by the global handler. */
public class DuplicateUsernameException extends RuntimeException {

  public DuplicateUsernameException(String username) {
    super("Username " + username + " already exists");
  }
}
