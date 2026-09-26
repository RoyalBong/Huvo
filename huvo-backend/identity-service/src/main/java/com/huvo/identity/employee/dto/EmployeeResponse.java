package com.huvo.identity.employee.dto;

import com.huvo.identity.employee.entity.Employee;

/**
 * Read model for the employee API ({@link EmployeeResponse} with salary) — ADMIN/HR only, or a
 * caller viewing their own record.
 *
 * <p>Every other authenticated caller gets {@link EmployeeSummaryResponse} instead, because pay is
 * not org-chart data: an employee browsing the directory has no business seeing what everyone else
 * earns. The shape is chosen per request in the controller rather than by nulling the field, so
 * {@code salary} is absent from the JSON entirely instead of present-and-zero.
 */
public record EmployeeResponse(Long id, String name, String departmentId, double salary) {

  public static EmployeeResponse from(Employee employee) {
    return new EmployeeResponse(
        employee.getId(), employee.getName(), employee.getDepartmentId(), employee.getSalary());
  }
}
