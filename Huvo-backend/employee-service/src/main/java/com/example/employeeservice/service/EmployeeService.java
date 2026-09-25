package com.example.employeeservice.service;

import com.example.employeeservice.entity.Employee;
import java.util.List;
import java.util.Optional;

public interface EmployeeService {

    List<Employee> getAllEmployees();

    Optional<Employee> getEmployeeById(Long id);

    Employee createEmployee(Employee employee);

    /**
     * @throws ResourceNotFoundException if no employee with {@code id} exists
     */
    Employee updateEmployee(Long id, Employee employee);

    /**
     * @throws ResourceNotFoundException if no employee with {@code id} exists
     */
    void deleteEmployee(Long id);
}