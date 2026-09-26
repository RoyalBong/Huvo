package com.huvo.identity.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.huvo.identity.auth.dto.AuthResponse;
import com.huvo.identity.auth.entity.AccessRole;
import com.huvo.identity.auth.entity.AppUser;
import com.huvo.identity.auth.messaging.LoginEventPublisher;
import com.huvo.identity.auth.repository.AppUserRepository;
import com.huvo.identity.exception.DuplicateUsernameException;
import com.huvo.identity.exception.InvalidCredentialsException;
import com.huvo.security.HuvoTokenService;
import com.huvo.security.InvalidTokenException;

import lombok.RequiredArgsConstructor;

/**
 * Login, refresh and logout (Huvo_Backend_Context.md Section 4.2). Issues short-lived access tokens
 * (15 min) plus rotating 7-day refresh tokens; only a SHA-256 hash of the current refresh token is
 * stored, so a database read never yields a usable token.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AppUserRepository users;
  private final HuvoTokenService tokens;
  private final BCryptPasswordEncoder passwordEncoder;
  private final LoginEventPublisher loginEvents;

  @Value("${huvo.auth.bootstrap-username:}")
  private String bootstrapUsername;

  @Value("${huvo.auth.bootstrap-password:}")
  private String bootstrapPassword;

  /**
   * Creates the first ADMIN when the user table is empty and bootstrap credentials are configured.
   * Runs once at startup via {@code AuthBootstrap}; never a migration, never seed data (Section
   * 2.1).
   *
   * @return true when a bootstrap user was created, false when there was nothing to do.
   */
  public boolean bootstrapAdminIfEmpty() {
    if (users.count() > 0) {
      return false;
    }
    if (bootstrapUsername == null
        || bootstrapUsername.isBlank()
        || bootstrapPassword == null
        || bootstrapPassword.isBlank()) {
      return false;
    }
    AppUser admin = new AppUser();
    admin.setUsername(bootstrapUsername.toLowerCase());
    admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
    admin.setRole(AccessRole.ADMIN.name());
    users.save(admin);
    return true;
  }

  /**
   * Validates credentials and returns a fresh access + refresh token pair.
   *
   * <p>The login instant is captured here, immediately after the password verifies and before any
   * token work, and handed to the event. It must not be derived from the event envelope's {@code
   * occurredAt}: that is stamped later, during token issuance and serialisation, so reusing it
   * would shift every {@code login_timestamp} a little later and, against Section 5.2's 15-minute
   * grace period, could mark a genuinely on-time login as late.
   *
   * @param username the submitted username
   * @param rawPassword the submitted password
   * @param sourceIp the client address for the {@code login_event} audit columns, or null
   * @return the token pair
   */
  public AuthResponse login(String username, String rawPassword, String sourceIp) {
    AppUser user = findByUsername(username);
    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    // Captured at credential verification, not at publish time - see the method note.
    OffsetDateTime loginTimestamp = OffsetDateTime.now();
    AuthResponse response = issueTokenPair(user);
    // user.login.success drives the lateness engine (Sections 5.2, 7); publishing it can never
    // fail a login that already succeeded - LoginEventPublisher swallows broker errors.
    loginEvents.publishLoginSuccess(user, loginTimestamp, sourceIp);
    return response;
  }

  /**
   * Rotates the refresh token: the presented token must match the stored hash, then a new pair is
   * issued and the old refresh token stops working.
   */
  public AuthResponse refresh(String refreshToken) {
    String userId;
    try {
      userId = tokens.validateRefreshToken(refreshToken);
    } catch (InvalidTokenException e) {
      throw new InvalidCredentialsException();
    }
    AppUser user = findById(userId);
    if (user.getRefreshTokenHash() == null
        || !user.getRefreshTokenHash().equals(sha256Hex(refreshToken))) {
      throw new InvalidCredentialsException();
    }
    return issueTokenPair(user);
  }

  /**
   * Clears the stored refresh-token hash so the caller's current refresh token stops working.
   * Idempotent: an unknown user (deleted between login and logout) is not an error.
   */
  public void logout(Long userId) {
    users
        .findById(userId)
        .ifPresent(
            user -> {
              user.setRefreshTokenHash(null);
              users.save(user);
            });
  }

  /** Every login, for the ADMIN user list. Read-only - creation stays in {@link #createUser}. */
  public List<AppUser> listUsers() {
    return users.findAll();
  }

  /** Creates an additional login. ADMIN-only - enforced by {@code @PreAuthorize} on the caller. */
  public AppUser createUser(
      String username, String rawPassword, String role, Long employeeId, List<Long> departmentIds) {
    if (!AccessRole.isValid(role)) {
      throw new IllegalArgumentException("Unknown role: " + role);
    }
    String normalised = username.toLowerCase();
    if (users.findByUsername(normalised).isPresent()) {
      throw new DuplicateUsernameException(normalised);
    }
    AppUser user = new AppUser();
    user.setUsername(normalised);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user.setEmployeeId(employeeId);
    if (departmentIds != null) {
      user.getDepartmentIds().addAll(departmentIds);
    }
    return users.save(user);
  }

  private AuthResponse issueTokenPair(AppUser user) {
    List<Long> departmentIds = List.copyOf(user.getDepartmentIds());
    String accessToken =
        tokens.issueAccessToken(
            user.getId().toString(), user.getRole(), departmentIds, user.getEmployeeId());
    String refreshToken = tokens.issueRefreshToken(user.getId().toString());
    user.setRefreshTokenHash(sha256Hex(refreshToken));
    users.save(user);
    return new AuthResponse(accessToken, refreshToken);
  }

  private AppUser findByUsername(String username) {
    String normalised = username == null ? "" : username.toLowerCase();
    return users.findByUsername(normalised).orElseThrow(InvalidCredentialsException::new);
  }

  private AppUser findById(String userId) {
    long id;
    try {
      id = Long.parseLong(userId);
    } catch (NumberFormatException e) {
      throw new InvalidCredentialsException();
    }
    return users.findById(id).orElseThrow(InvalidCredentialsException::new);
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for refresh-token hashes", e);
    }
  }
}
