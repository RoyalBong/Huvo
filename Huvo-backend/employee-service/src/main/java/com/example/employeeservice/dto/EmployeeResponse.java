package com.example.employeeservice.dto;

import com.example.employeeservice.entity.Employee;

/** Read model returned by the employee API (§10). */
public record EmployeeResponse(Long id, String name, String departmentId, double salary) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getDepartmentId(),
                employee.getSalary());
    }
}
