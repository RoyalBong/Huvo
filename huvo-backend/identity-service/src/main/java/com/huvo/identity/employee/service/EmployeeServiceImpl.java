package com.huvo.identity.employee.service;

import java.util.LinkedHashMap;
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
    // Best-effort: the row above is already committed, so a failed audit write must not be
    // reported to the client as a failed create. See AuditRecorder#record.
    audit.record(
        EventTypes.EMPLOYEE_CREATED,
        ENTITY,
        String.valueOf(savedEmployee.getId()),
        details(savedEmployee));
    return savedEmployee;
  }

  /**
   * Audit detail built without {@code Map.of}, which rejects null values. The audit call must never
   * be the reason a mutation fails, and an employee may legitimately have no department yet.
   */
  private static Map<String, String> details(Employee employee) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("name", employee.getName());
    putIfPresent(details, "departmentId", employee.getDepartmentId());
    return details;
  }

  private static void putIfPresent(Map<String, String> details, String key, String value) {
    if (value != null) {
      details.put(key, value);
    }
  }

  @Override
  public Employee updateEmployee(Long id, Employee employee) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Employee " + id + " was not found");
    }
    employee.setId(id);
    Employee updatedEmployee = repository.save(employee);
    eventPublisher.publishEmployeeUpdated(employee.getId());
    audit.record(EventTypes.EMPLOYEE_UPDATED, ENTITY, String.valueOf(id), details(updatedEmployee));
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
