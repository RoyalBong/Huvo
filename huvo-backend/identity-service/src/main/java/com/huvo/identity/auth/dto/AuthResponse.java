package com.huvo.identity.auth.dto;

/** Read model returned by login/refresh - the access token the client sends as a Bearer token. */
public record AuthResponse(String accessToken, String refreshToken, String tokenType) {

  public AuthResponse(String accessToken, String refreshToken) {
    this(accessToken, refreshToken, "Bearer");
  }
}
