package com.huvo.identity.employee.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.huvo.identity.employee.dto.EmployeeRequest;
import com.huvo.identity.employee.dto.EmployeeResponse;
import com.huvo.identity.employee.service.EmployeeService;
import com.huvo.identity.exception.ResourceNotFoundException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Employee API. Works exclusively with DTOs (Huvo Backend_Context.md §10) - JPA entities are never
 * returned to a client, and all failures come back through {@link
 * com.huvo.identity.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

  private final EmployeeService service;

  /**
   * Reads are open to any authenticated role for now: the org directory is company-wide reference
   * data, not a private record. Scoping these by {@code departmentIds} is a product decision (see
   * the note on {@link #getAllEmployees()}), not a code one.
   */
  @GetMapping
  public List<EmployeeResponse> getAllEmployees() {
    return service.getAllEmployees().stream().map(EmployeeResponse::from).toList();
  }

  @GetMapping("/{id}")
  public EmployeeResponse getEmployeeById(@PathVariable Long id) {
    return service
        .getEmployeeById(id)
        .map(EmployeeResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " was not found"));
  }

  /**
   * Changing the employee record is an HR duty (Section 4.1): a MANAGER manages their team's
   * attendance and tasks, not headcount, pay or reporting lines. Enforcement is per method rather
   * than class-level so the reads above stay open to every authenticated role.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<EmployeeResponse> createEmployee(
      @Valid @RequestBody EmployeeRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(EmployeeResponse.from(service.createEmployee(request.toEntity())));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public EmployeeResponse updateEmployee(
      @PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
    return EmployeeResponse.from(service.updateEmployee(id, request.toEntity()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
    service.deleteEmployee(id);
    return ResponseEntity.noContent().build();
  }
}
