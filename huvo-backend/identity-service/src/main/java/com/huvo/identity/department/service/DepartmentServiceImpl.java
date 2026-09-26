package com.huvo.identity.department.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.huvo.identity.department.entity.Department;
import com.huvo.identity.department.messaging.DepartmentEventPublisher;
import com.huvo.identity.department.repository.DepartmentRepository;
import com.huvo.identity.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

  private final DepartmentRepository repository;

  private final DepartmentEventPublisher eventPublisher;

  @Override
  public List<Department> getAllDepartments() {
    return repository.findAll();
  }

  @Override
  public Optional<Department> getDepartmentById(Long id) {
    return repository.findById(id);
  }

  @Override
  public Department createDepartment(Department department) {
    Department savedDepartment = repository.save(department);
    eventPublisher.publishDepartmentCreated(savedDepartment.getId());
    return savedDepartment;
  }

  @Override
  public Department updateDepartment(Long id, Department department) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Department " + id + " was not found");
    }
    department.setId(id);
    Department updatedDepartment = repository.save(department);
    eventPublisher.publishDepartmentUpdated(updatedDepartment.getId());
    return updatedDepartment;
  }

  @Override
  public void deleteDepartment(Long id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Department " + id + " was not found");
    }
    repository.deleteById(id);
    eventPublisher.publishDepartmentDeleted(id);
  }
}
