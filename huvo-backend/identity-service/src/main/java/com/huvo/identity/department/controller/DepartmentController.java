package com.huvo.identity.department.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.huvo.identity.department.dto.DepartmentRequest;
import com.huvo.identity.department.dto.DepartmentResponse;
import com.huvo.identity.department.service.DepartmentService;
import com.huvo.identity.exception.ResourceNotFoundException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Department API. Works exclusively with DTOs (Huvo Backend_Context.md §10) - JPA entities are
 * never returned to a client, and all failures come back through {@link
 * com.huvo.identity.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService service;

  /**
   * Reads are open to any authenticated role for now: the department list is company-wide reference
   * data that the frontend needs to populate pickers and labels. Narrowing it to a caller's {@code
   * departmentIds} would break those pickers, so it is flagged rather than decided here.
   */
  @GetMapping
  public List<DepartmentResponse> getAllDepartments() {
    return service.getAllDepartments().stream().map(DepartmentResponse::from).toList();
  }

  @GetMapping("/{id}")
  public DepartmentResponse getDepartmentById(@PathVariable Long id) {
    return service
        .getDepartmentById(id)
        .map(DepartmentResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("Department " + id + " was not found"));
  }

  /**
   * The org structure is HR-maintained (Section 4.1). A MANAGER can see the tree but not reshape
   * it, and a department rename cascades to every employee's {@code departmentId} label.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<DepartmentResponse> createDepartment(
      @Valid @RequestBody DepartmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(DepartmentResponse.from(service.createDepartment(request.toEntity())));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public DepartmentResponse updateDepartment(
      @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
    return DepartmentResponse.from(service.updateDepartment(id, request.toEntity()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
    service.deleteDepartment(id);
    return ResponseEntity.noContent().build();
  }
}
