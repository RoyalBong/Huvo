package com.huvo.identity.auth.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login-creation write model (ADMIN-only endpoint). The raw password appears here and is hashed
 * immediately by {@code AuthService}; it never reaches a response or an entity field.
 */
public record UserCreateRequest(
    @NotBlank(message = "username must not be blank")
        @Size(max = 190, message = "username must be at most 190 characters")
        String username,
    @NotBlank(message = "password must not be blank")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,
    @NotBlank(message = "role must not be blank") String role,
    Long employeeId,
    List<Long> departmentIds) {}
