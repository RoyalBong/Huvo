package com.huvo.identity.auth.dto;

import java.util.List;

import com.huvo.identity.auth.entity.AppUser;

/** Read model for a login. Never exposes the password hash or the refresh-token hash. */
public record UserResponse(
    Long id, String username, String role, Long employeeId, List<Long> departmentIds) {

  public static UserResponse from(AppUser user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        user.getEmployeeId(),
        List.copyOf(user.getDepartmentIds()));
  }
}
