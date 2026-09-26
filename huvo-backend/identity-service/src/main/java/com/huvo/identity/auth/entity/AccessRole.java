package com.huvo.identity.auth.entity;

/** Access Roles for authorization (Huvo_Backend_Context.md Section 4.1). */
public enum AccessRole {
  ADMIN,
  HR,
  MANAGER,
  EMPLOYEE;

  public static boolean isValid(String role) {
    if (role == null) {
      return false;
    }
    for (AccessRole candidate : values()) {
      if (candidate.name().equals(role)) {
        return true;
      }
    }
    return false;
  }
}
