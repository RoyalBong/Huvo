package com.huvo.identity.auth.dto;

import java.util.List;

import com.huvo.security.HuvoPrincipal;

/** Read model of the claims a caller's access token carries (Huvo_Backend_Context.md §4.2). */
public record PrincipalResponse(
    String userId, String role, List<Long> departmentIds, Long employeeId) {

  public static PrincipalResponse from(HuvoPrincipal principal) {
    return new PrincipalResponse(
        principal.userId(), principal.role(), principal.departmentIds(), principal.employeeId());
  }
}
