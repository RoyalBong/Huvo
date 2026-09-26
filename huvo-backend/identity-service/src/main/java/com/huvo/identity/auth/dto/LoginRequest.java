package com.huvo.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Login write model - raw password only ever appears here, never in a response or an entity. */
public record LoginRequest(
    @NotBlank(message = "username must not be blank") String username,
    @NotBlank(message = "password must not be blank") String password) {}
