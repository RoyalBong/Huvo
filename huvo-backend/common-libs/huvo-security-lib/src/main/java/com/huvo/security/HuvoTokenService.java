package com.huvo.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates Huvo JWTs (Huvo_Backend_Context.md Section 4.2). The ONLY class in the
 * codebase that touches {@code JWT_SIGNING_KEY} directly.
 *
 * <p>Claims contract: {@code sub=userId, role, departmentIds, employeeId, iat, exp} plus {@code
 * jti} for tracing and {@code typ=access|refresh} so a refresh token can never be used as an access
 * token. HS256. Access 15 min, refresh 7 days.
 *
 * <p>Framework-free on purpose: no Spring, no servlet types. Services wrap this in a filter or
 * interceptor and map {@link ExpiredTokenException}/{@link InvalidTokenException} to 401.
 */
public class HuvoTokenService {

  /** Token type claim - enforced on validation so token kinds cannot be swapped. */
  public static final String TOKEN_TYPE_CLAIM = "typ";

  public static final String ACCESS_TOKEN_TYPE = "access";
  public static final String REFRESH_TOKEN_TYPE = "refresh";

  /** Section 4.2 lifetimes: short-lived access, week-long refresh. */
  public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

  public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

  private final SecretKey signingKey;

  /**
   * @param signingKeyValue raw value of {@code JWT_SIGNING_KEY}; must be >= 32 bytes for HS256. A
   *     fail-fast {@link IllegalArgumentException} here beats a runtime signature failure later.
   */
  public HuvoTokenService(String signingKeyValue) {
    if (signingKeyValue == null || signingKeyValue.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("JWT signing key must be at least 32 bytes");
    }
    this.signingKey = Keys.hmacShaKeyFor(signingKeyValue.getBytes(StandardCharsets.UTF_8));
  }

  public String issueAccessToken(
      String userId, String role, List<Long> departmentIds, Long employeeId) {
    return issueToken(userId, role, departmentIds, employeeId, ACCESS_TOKEN_TYPE, ACCESS_TOKEN_TTL);
  }

  public String issueRefreshToken(String userId) {
    return issueToken(userId, null, List.of(), null, REFRESH_TOKEN_TYPE, REFRESH_TOKEN_TTL);
  }

  /** Validates signature + expiry and enforces the token kind. Never returns a null principal. */
  public HuvoPrincipal validateAccessToken(String token) {
    return validate(token, ACCESS_TOKEN_TYPE);
  }

  /** Validates a refresh token and returns the subject it was issued for. */
  public String validateRefreshToken(String token) {
    return validate(token, REFRESH_TOKEN_TYPE).userId();
  }

  private String issueToken(
      String userId,
      String role,
      List<Long> departmentIds,
      Long employeeId,
      String tokenType,
      Duration ttl) {
    Instant now = Instant.now();
    var builder =
        Jwts.builder()
            .subject(userId)
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(signingKey);
    if (role != null) {
      builder.claim("role", role);
    }
    if (departmentIds != null && !departmentIds.isEmpty()) {
      builder.claim("departmentIds", new ArrayList<>(departmentIds));
    }
    if (employeeId != null) {
      builder.claim("employeeId", employeeId);
    }
    return builder.compact();
  }

  private HuvoPrincipal validate(String token, String expectedType) {
    if (token == null || token.isBlank()) {
      throw new InvalidTokenException("Missing bearer token");
    }
    Claims claims;
    try {
      claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      throw new ExpiredTokenException("Token has expired", e);
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidTokenException("Token is invalid", e);
    }
    String actualType = claims.get(TOKEN_TYPE_CLAIM, String.class);
    if (!expectedType.equals(actualType)) {
      throw new InvalidTokenException(
          "Token is not an " + expectedType + " token (typ=" + actualType + ")");
    }
    if (ACCESS_TOKEN_TYPE.equals(expectedType)) {
      String role = claims.get("role", String.class);
      if (claims.getSubject() == null || role == null || role.isBlank()) {
        throw new InvalidTokenException("Access token lacks required claims (sub, role)");
      }
      return new HuvoPrincipal(
          claims.getSubject(), role, toDepartmentIds(claims), toEmployeeId(claims));
    }
    if (claims.getSubject() == null || claims.getSubject().isBlank()) {
      throw new InvalidTokenException("Refresh token lacks a subject");
    }
    return new HuvoPrincipal(claims.getSubject(), null, List.of(), null);
  }

  private static List<Long> toDepartmentIds(Claims claims) {
    List<?> raw = claims.get("departmentIds", List.class);
    if (raw == null) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>(raw.size());
    for (Object value : raw) {
      if (value instanceof Number number) {
        ids.add(number.longValue());
      } else {
        throw new InvalidTokenException("Access token has a non-numeric departmentId");
      }
    }
    return ids;
  }

  private static Long toEmployeeId(Claims claims) {
    Object raw = claims.get("employeeId");
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number number) {
      return number.longValue();
    }
    throw new InvalidTokenException("Access token has a non-numeric employeeId");
  }
}
