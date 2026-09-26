package com.huvo.identity.employee.dto;

import com.huvo.identity.employee.entity.Employee;

/**
 * The default read model for the employee API: who works here and where — no pay.
 *
 * <p>Returned to every authenticated caller that is not entitled to salary. Kept as its own type
 * rather than a nullable field on {@link EmployeeResponse} so the two shapes cannot drift, and so
 * adding a new sensitive column to {@link EmployeeResponse} does not silently leak here.
 */
public record EmployeeSummaryResponse(Long id, String name, String departmentId) {

  public static EmployeeSummaryResponse from(Employee employee) {
    return new EmployeeSummaryResponse(
        employee.getId(), employee.getName(), employee.getDepartmentId());
  }
}
