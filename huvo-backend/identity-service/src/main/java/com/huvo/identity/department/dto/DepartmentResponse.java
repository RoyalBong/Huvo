package com.huvo.identity.department.dto;

import com.huvo.identity.department.entity.Department;

/** Read model returned by the department API (§10). */
public record DepartmentResponse(Long id, String name, String location) {

  public static DepartmentResponse from(Department department) {
    return new DepartmentResponse(
        department.getId(), department.getName(), department.getLocation());
  }
}
