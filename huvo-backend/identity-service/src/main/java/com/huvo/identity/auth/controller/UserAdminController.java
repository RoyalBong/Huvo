package com.huvo.identity.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.identity.auth.dto.UserCreateRequest;
import com.huvo.identity.auth.dto.UserResponse;
import com.huvo.identity.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Login administration (Huvo_Backend_Context.md Sections 4.1, 4.2). Creating a login means granting
 * an Access Role, so the whole controller is ADMIN-only - nobody can escalate themselves or anyone
 * else through the API.
 */
@RestController
@RequestMapping("/api/auth/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

  private final AuthService service;

  @GetMapping
  public List<UserResponse> listUsers() {
    return service.listUsers().stream().map(UserResponse::from).toList();
  }

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            UserResponse.from(
                service.createUser(
                    request.username(),
                    request.password(),
                    request.role(),
                    request.employeeId(),
                    request.departmentIds())));
  }
}
