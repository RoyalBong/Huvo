package com.huvo.identity.employee.dto;

import com.huvo.identity.employee.entity.Employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Write model for the employee API (Huvo Backend_Context.md §10 - DTOs at API boundaries always,
 * never JPA entities).
 */
public record EmployeeRequest(
    @NotBlank(message = "name must not be blank") String name,
    String departmentId,
    @PositiveOrZero(message = "salary must not be negative") double salary) {

  /** Builds a new entity; the service assigns the id. */
  public Employee toEntity() {
    return new Employee(null, name, departmentId, salary);
  }
}
