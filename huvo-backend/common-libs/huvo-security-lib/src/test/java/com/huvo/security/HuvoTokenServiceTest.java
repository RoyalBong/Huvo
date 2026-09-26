package com.huvo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Edge cases the whole platform depends on: every future service reuses {@link HuvoTokenService},
 * so expiry/signature/type bugs here would silently open every endpoint.
 */
class HuvoTokenServiceTest {

  private static final String SIGNING_KEY = "test-only-signing-key-that-is-long-enough-for-hs256!!";

  private HuvoTokenService tokens;

  @BeforeEach
  void setUp() {
    tokens = new HuvoTokenService(SIGNING_KEY);
  }

  @Test
  void accessTokenRoundTripsEveryClaimInTheSection42Contract() {
    String token = tokens.issueAccessToken("user-1", "MANAGER", List.of(3L, 7L), 1042L);

    HuvoPrincipal principal = tokens.validateAccessToken(token);

    assertThat(principal.userId()).isEqualTo("user-1");
    assertThat(principal.role()).isEqualTo("MANAGER");
    assertThat(principal.departmentIds()).containsExactly(3L, 7L);
    assertThat(principal.employeeId()).isEqualTo(1042L);
  }

  @Test
  void refreshTokenValidatesToItsSubject() {
    String token = tokens.issueRefreshToken("user-1");

    assertThat(tokens.validateRefreshToken(token)).isEqualTo("user-1");
  }

  @Test
  void refreshTokenCannotBeUsedAsAnAccessToken() {
    String refresh = tokens.issueRefreshToken("user-1");

    assertThatThrownBy(() -> tokens.validateAccessToken(refresh))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void accessTokenCannotBeUsedAsARefreshToken() {
    String access = tokens.issueAccessToken("user-1", "EMPLOYEE", List.of(), null);

    assertThatThrownBy(() -> tokens.validateRefreshToken(access))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void tamperedSignatureIsRejected() {
    String token = tokens.issueAccessToken("user-1", "EMPLOYEE", List.of(), null);
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    assertThatThrownBy(() -> tokens.validateAccessToken(tampered))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void tokenSignedByAnotherKeyIsRejected() {
    HuvoTokenService other = new HuvoTokenService("a-completely-different-signing-key-for-hs256!!");
    String foreign = other.issueAccessToken("user-1", "EMPLOYEE", List.of(), null);

    assertThatThrownBy(() -> tokens.validateAccessToken(foreign))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void expiredTokenMapsToExpiredNotInvalid() {
    String expired = expiredAccessToken();

    assertThatThrownBy(() -> tokens.validateAccessToken(expired))
        .isInstanceOf(ExpiredTokenException.class);
  }

  @Test
  void missingAndBlankTokensAreInvalid() {
    assertThatThrownBy(() -> tokens.validateAccessToken(null))
        .isInstanceOf(InvalidTokenException.class);
    assertThatThrownBy(() -> tokens.validateAccessToken("   "))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shortSigningKeyFailsFastAtConstruction() {
    assertThatThrownBy(() -> new HuvoTokenService("too-short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accessTokenWithoutRoleIsInvalid() {
    SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
    Instant now = Instant.now();
    String roleless =
        Jwts.builder()
            .subject("user-1")
            .claim(HuvoTokenService.TOKEN_TYPE_CLAIM, HuvoTokenService.ACCESS_TOKEN_TYPE)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(HuvoTokenService.ACCESS_TOKEN_TTL)))
            .signWith(key)
            .compact();

    assertThatThrownBy(() -> tokens.validateAccessToken(roleless))
        .isInstanceOf(InvalidTokenException.class);
  }

  private static String expiredAccessToken() {
    // Builds a token that is already expired without waiting out the 15-minute TTL.
    SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
    Instant past = Instant.now().minusSeconds(3600);
    return Jwts.builder()
        .subject("user-1")
        .claim("role", "EMPLOYEE")
        .claim(HuvoTokenService.TOKEN_TYPE_CLAIM, HuvoTokenService.ACCESS_TOKEN_TYPE)
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(past))
        .expiration(Date.from(past.plusSeconds(60)))
        .signWith(key)
        .compact();
  }
}
