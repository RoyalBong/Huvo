package com.huvo.identity.department.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.huvo.events.EventTypes;
import com.huvo.identity.audit.AuditRecorder;
import com.huvo.identity.department.entity.Department;
import com.huvo.identity.department.messaging.DepartmentEventPublisher;
import com.huvo.identity.department.repository.DepartmentRepository;
import com.huvo.identity.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

  /**
   * The partition key and the audit "entity" value: all history for one department is one query.
   */
  private static final String ENTITY = "department";

  private final DepartmentRepository repository;

  private final DepartmentEventPublisher eventPublisher;

  private final AuditRecorder audit;

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
    audit.record(
        EventTypes.DEPARTMENT_CREATED,
        ENTITY,
        String.valueOf(savedDepartment.getId()),
        Map.of("name", savedDepartment.getName(), "location", savedDepartment.getLocation()));
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
    audit.record(
        EventTypes.DEPARTMENT_UPDATED,
        ENTITY,
        String.valueOf(id),
        Map.of("name", updatedDepartment.getName(), "location", updatedDepartment.getLocation()));
    return updatedDepartment;
  }

  @Override
  public void deleteDepartment(Long id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("Department " + id + " was not found");
    }
    repository.deleteById(id);
    eventPublisher.publishDepartmentDeleted(id);
    audit.record(EventTypes.DEPARTMENT_DELETED, ENTITY, String.valueOf(id), Map.of());
  }
}
