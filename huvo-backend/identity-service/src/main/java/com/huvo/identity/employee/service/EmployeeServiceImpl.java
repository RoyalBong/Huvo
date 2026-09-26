package com.huvo.identity.employee.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.huvo.events.EventTypes;
import com.huvo.identity.audit.AuditRecorder;
import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.employee.messaging.EmployeeEventPublisher;
import com.huvo.identity.employee.repository.EmployeeRepository;
import com.huvo.identity.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

  /** The partition key and the audit "entity" value: all history for one employee is one query. */
  private static final String ENTITY = "employee";

  private final EmployeeRepository repository;

  private final EmployeeEventPublisher eventPublisher;

  private final AuditRecorder audit;

  @Override
  public List<Employee> getAllEmployees() {
    return repository.findAll();
  }

  @Override
  public Optional<Employee> getEmployeeById(Long id) {
    return repository.findById(id);
  }

  @Override
  public Employee createEmployee(Employee employee) {
    Employee savedEmployee = repository.save(employee);
    eventPublisher.publishEmployeeCreated(savedEmployee.getId());
    audit.record(
        EventTypes.EMPLOYEE_CREATED,
        ENTITY,
        String.valueOf(savedEmployee.getId()),
        Map.of("name", savedEmployee.getName(), "departmentId", savedEmployee.getDepartmentId()));
    return savedEmployee;
  }

  @Override
  public Employee updateEmployee(Long id, Employee employee) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Employee " + id + " was not found");
    }
    employee.setId(id);
    Employee updatedEmployee = repository.save(employee);
    eventPublisher.publishEmployeeUpdated(employee.getId());
    audit.record(
        EventTypes.EMPLOYEE_UPDATED,
        ENTITY,
        String.valueOf(id),
        Map.of(
            "name", updatedEmployee.getName(), "departmentId", updatedEmployee.getDepartmentId()));
    return updatedEmployee;
  }

  @Override
  public void deleteEmployee(Long id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Employee " + id + " was not found");
    }
    repository.deleteById(id);
    eventPublisher.publishEmployeeDeleted(id);
    audit.record(EventTypes.EMPLOYEE_DELETED, ENTITY, String.valueOf(id), Map.of());
  }
}
