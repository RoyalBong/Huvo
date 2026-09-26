package com.huvo.identity.department.service;

import java.util.LinkedHashMap;
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
    // Best-effort: the row above is already committed, so a failed audit write must not be
    // reported to the client as a failed create. See AuditRecorder#record.
    audit.record(
        EventTypes.DEPARTMENT_CREATED,
        ENTITY,
        String.valueOf(savedDepartment.getId()),
        details(savedDepartment));
    return savedDepartment;
  }

  /**
   * Audit detail built without {@code Map.of}, which rejects null values. The audit call must never
   * be the reason a mutation fails, and a department may legitimately have no location yet.
   */
  private static Map<String, String> details(Department department) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("name", department.getName());
    if (department.getLocation() != null) {
      details.put("location", department.getLocation());
    }
    return details;
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
        EventTypes.DEPARTMENT_UPDATED, ENTITY, String.valueOf(id), details(updatedDepartment));
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
