package com.huvo.identity.department.dto;

import com.huvo.identity.department.entity.Department;

import jakarta.validation.constraints.NotBlank;

/**
 * Write model for the department API (Huvo Backend_Context.md §10 - DTOs at API boundaries always,
 * never JPA entities).
 */
public record DepartmentRequest(
    @NotBlank(message = "name must not be blank") String name, String location) {

  /** Builds a new entity; the service assigns the id. */
  public Department toEntity() {
    return new Department(null, name, location);
  }
}
