package com.huvo.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Refresh write model - the opaque refresh token issued alongside the access token. */
public record RefreshRequest(
    @NotBlank(message = "refresh token must not be blank") String refreshToken) {}
