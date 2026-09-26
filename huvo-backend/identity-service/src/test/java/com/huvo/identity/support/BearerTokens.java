package com.huvo.identity.support;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.huvo.security.HuvoTokenService;

/**
 * Builds the {@code Authorization: Bearer} header a real client would send, so the MockMvc tests
 * exercise the actual {@code JwtAuthenticationFilter} instead of short-circuiting the security
 * context.
 *
 * <p>Prefer this over {@code @WithMockUser}: the filter chain only understands a signed token, and
 * a mock authentication would hide regressions in the token parsing, the {@code ROLE_} authority
 * mapping and the claim contract of {@link HuvoTokenService} (§4.2).
 */
public final class BearerTokens {

  /** A user id that satisfies the {@code principal.userId()} parse in the auth controller. */
  private static final String TEST_USER_ID = "1";

  private static final Long TEST_EMPLOYEE_ID = 1L;

  private BearerTokens() {}

  /**
   * An ADMIN caller - the role {@code @PreAuthorize("hasRole('ADMIN')")} endpoints expect.
   *
   * @param tokens the token service the filter under test will validate against.
   */
  public static RequestPostProcessor forAdmin(HuvoTokenService tokens) {
    return forRole(tokens, "ADMIN");
  }

  /**
   * A caller holding a single access role, with the department scope the endpoints re-check.
   *
   * @param tokens the token service the filter under test will validate against.
   * @param role one of ADMIN, HR, MANAGER, EMPLOYEE (§4.1).
   */
  public static RequestPostProcessor forRole(HuvoTokenService tokens, String role) {
    return forRoleAndEmployee(tokens, role, TEST_EMPLOYEE_ID);
  }

  /**
   * As {@link #forRole}, but with an explicit {@code employeeId} claim, for tests that exercise a
   * self-view exception where the caller is only entitled to their own record.
   *
   * @param tokens the token service the filter under test will validate against.
   * @param role one of ADMIN, HR, MANAGER, EMPLOYEE (§4.1).
   * @param employeeId the caller's own employee record, or null when they are not linked to one.
   */
  public static RequestPostProcessor forRoleAndEmployee(
      HuvoTokenService tokens, String role, Long employeeId) {
    return request -> {
      String accessToken =
          tokens.issueAccessToken(TEST_USER_ID, role, List.of(1L, 2L, 3L), employeeId);
      request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
      return request;
    };
  }
}
