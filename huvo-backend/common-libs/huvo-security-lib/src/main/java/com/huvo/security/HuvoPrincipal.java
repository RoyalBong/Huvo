package com.huvo.security;

import java.util.List;

/**
 * The authenticated caller every Huvo service sees after validating the JWT itself
 * (Huvo_Backend_Context.md Section 4.2). There is no central auth proxy - Nginx forwards the {@code
 * Authorization} header untouched and each service builds this from the token claims via {@link
 * HuvoTokenService}.
 */
public record HuvoPrincipal(String userId, String role, List<Long> departmentIds, Long employeeId) {

  public HuvoPrincipal {
    departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
  }
}
