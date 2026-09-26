package com.huvo.identity.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.identity.auth.dto.AuthResponse;
import com.huvo.identity.auth.dto.LoginRequest;
import com.huvo.identity.auth.dto.PrincipalResponse;
import com.huvo.identity.auth.dto.RefreshRequest;
import com.huvo.identity.auth.service.AuthService;
import com.huvo.security.HuvoPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Login, token refresh and logout (Huvo_Backend_Context.md Section 4.2). {@code /login} and {@code
 * /refresh} are the only endpoints the client may call without a valid access token; every other
 * route in this service requires one.
 *
 * <p>The response is the token pair itself - identity-service never sets a cookie, so the browser
 * or mobile client decides where to store it (Section 4.2 allows httpOnly cookie or secure mobile
 * storage, and that choice belongs to the caller).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService service;

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return service.login(request.username(), request.password());
  }

  /** Exchanges a still-valid refresh token for a new pair; the presented one stops working. */
  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return service.refresh(request.refreshToken());
  }

  /** Revokes the caller's refresh token. Idempotent, so a client can always retry it. */
  @PostMapping("/logout")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal HuvoPrincipal principal) {
    service.logout(Long.parseLong(principal.userId()));
    return ResponseEntity.noContent().build();
  }

  /**
   * Echoes what the caller's token actually claims. Handy for a client that needs the {@code
   * departmentIds}/{@code employeeId} scope after a cold start, and it proves to an operator that
   * the signing key and the claims contract line up between services.
   */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public PrincipalResponse me(@AuthenticationPrincipal HuvoPrincipal principal) {
    return PrincipalResponse.from(principal);
  }
}
