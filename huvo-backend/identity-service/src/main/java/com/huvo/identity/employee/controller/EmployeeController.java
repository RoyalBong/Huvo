package com.huvo.identity.employee.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.huvo.identity.employee.dto.EmployeeRequest;
import com.huvo.identity.employee.dto.EmployeeResponse;
import com.huvo.identity.employee.dto.EmployeeSummaryResponse;
import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.employee.service.EmployeeService;
import com.huvo.identity.exception.ResourceNotFoundException;
import com.huvo.security.HuvoPrincipal;

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
   * The directory is open to any authenticated role — who works here is org-chart data, not private
   * — but pay is not. ADMIN/HR get the full representation; everyone else gets {@link
   * EmployeeSummaryResponse} with no salary field at all.
   *
   * <p>Returning a {@code List<?>} is deliberate: the element type varies by caller, and that is
   * what the frontend must handle. A single response type with an optional salary invites
   * accidental exposure; two named shapes cannot drift.
   */
  @GetMapping
  public List<?> getAllEmployees(@AuthenticationPrincipal HuvoPrincipal principal) {
    if (maySeeSalaryOfAnyone(principal)) {
      return service.getAllEmployees().stream().map(EmployeeResponse::from).toList();
    }
    return service.getAllEmployees().stream().map(EmployeeSummaryResponse::from).toList();
  }

  /**
   * Same pay rule as the list, plus the self-view exception: an employee may always read their own
   * salary. The list deliberately does not grant it — a caller is one row among many there, and the
   * shape is chosen once for the whole response.
   */
  @GetMapping("/{id}")
  public Object getEmployeeById(
      @PathVariable Long id, @AuthenticationPrincipal HuvoPrincipal principal) {
    Employee found =
        service
            .getEmployeeById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " was not found"));
    if (maySeeSalaryOfAnyone(principal) || isSelf(id, principal)) {
      return EmployeeResponse.from(found);
    }
    return EmployeeSummaryResponse.from(found);
  }

  /** Only ADMIN and HR may see anyone's salary (Section 4.1). */
  private static boolean maySeeSalaryOfAnyone(HuvoPrincipal principal) {
    return principal != null && ("ADMIN".equals(principal.role()) || "HR".equals(principal.role()));
  }

  /**
   * The self-view exception, compared against the {@code employeeId} claim (§4.2). Null-safe
   * because a token issued before the caller was linked to an employee record carries no such
   * claim, and {@code Long.equals(null)} is false — such a caller is nobody's self-view.
   */
  private static boolean isSelf(Long id, HuvoPrincipal principal) {
    return principal != null && id.equals(principal.employeeId());
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
