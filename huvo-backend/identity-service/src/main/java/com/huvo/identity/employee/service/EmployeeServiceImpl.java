package com.huvo.identity.employee.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.employee.messaging.EmployeeEventPublisher;
import com.huvo.identity.employee.repository.EmployeeRepository;
import com.huvo.identity.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

  private final EmployeeRepository repository;

  private final EmployeeEventPublisher eventPublisher;

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
    return updatedEmployee;
  }

  @Override
  public void deleteEmployee(Long id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Employee " + id + " was not found");
    }
    repository.deleteById(id);
    eventPublisher.publishEmployeeDeleted(id);
  }
}
