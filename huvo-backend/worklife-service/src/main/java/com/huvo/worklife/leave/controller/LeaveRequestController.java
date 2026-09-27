package com.huvo.worklife.leave.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.leave.entity.LeaveRequest;
import com.huvo.worklife.leave.service.LeaveService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * The leave API (Huvo_Backend_Context.md Section 7).
 *
 * <p>An application is filed for the caller's own {@code employeeId}, taken from the token rather
 * than the body. Accepting it from the body would let anyone file leave in someone else's name,
 * which is the kind of thing that reaches payroll.
 */
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveRequestController {

  private final LeaveService leave;

  /**
   * Files a leave application.
   *
   * @param principal the applicant
   * @param request the requested range, both ends inclusive
   * @return the pending request
   */
  @PostMapping
  public ResponseEntity<LeaveRequest> apply(
      @AuthenticationPrincipal HuvoPrincipal principal, @Valid @RequestBody ApplyRequest request) {
    LeaveRequest saved =
        leave.apply(principal, request.leaveType(), request.from(), request.to(), request.reason());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  /** The caller's own leave history. */
  @GetMapping("/mine")
  public List<LeaveRequest> mine(@AuthenticationPrincipal HuvoPrincipal principal) {
    if (principal.employeeId() == null) {
      throw new BusinessRuleException("Your account is not linked to an employee record");
    }
    return leave.history(principal.employeeId());
  }

  /** The approver's pending queue. */
  @GetMapping("/pending")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public List<LeaveRequest> pending() {
    return leave.pending();
  }

  /**
   * Approves an application, publishing {@code leave.approved} (Section 7).
   *
   * @param principal the approver
   * @param id the request to approve
   * @param request an optional note for the requester
   * @return the approved request
   */
  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public LeaveRequest approve(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @PathVariable Long id,
      @RequestBody(required = false) DecisionRequest request) {
    return leave.approve(principal, id, request == null ? null : request.note());
  }

  /**
   * Rejects an application, publishing {@code leave.rejected} (Section 7).
   *
   * @param principal the approver
   * @param id the request to reject
   * @param request why, which is required
   * @return the rejected request
   */
  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public LeaveRequest reject(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @PathVariable Long id,
      @RequestBody DecisionRequest request) {
    return leave.reject(principal, id, request.reason());
  }

  /**
   * A leave application (Section 7).
   *
   * @param leaveType e.g. {@code ANNUAL}
   * @param from the first day, inclusive
   * @param to the last day, inclusive
   * @param reason an optional note for the approver
   */
  public record ApplyRequest(
      @NotBlank String leaveType, @NotNull LocalDate from, @NotNull LocalDate to, String reason) {}

  /**
   * An approval or rejection (Section 7).
   *
   * @param reason why it was rejected; required for a rejection
   * @param note an optional note on an approval
   */
  public record DecisionRequest(String note, String reason) {}
}
