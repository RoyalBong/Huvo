package com.huvo.worklife.leave.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.audit.AuditClient;
import com.huvo.audit.AuditEntry;
import com.huvo.events.EventTypes;
import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.exception.LeaveConflictException;
import com.huvo.worklife.exception.NotFoundException;
import com.huvo.worklife.leave.LeaveStatus;
import com.huvo.worklife.leave.entity.LeaveRequest;
import com.huvo.worklife.leave.repository.LeaveRequestRepository;
import com.huvo.worklife.messaging.WorklifeEventPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * Leave applications and their decisions (Huvo_Backend_Context.md Section 7).
 *
 * <p>This is the <strong>producer</strong> of the contract attendance-service already consumes. The
 * payload fields and their meaning are fixed by that consumer - {@code employeeId, from, to,
 * leaveId}, both dates inclusive - so the shape is taken from {@link EventTypes} rather than built
 * here, which is what keeps the two from drifting. Do not reshuffle these fields: attendance marks
 * every covered day {@code ON_LEAVE}, so a changed range silently changes which days that is.
 *
 * <p>Approvals and rejections are guaranteed-audited: with no audit client configured they refuse
 * rather than proceed, because an approval that cannot be recorded is how leave gets approved
 * invisibly.
 */
@Slf4j
@Service
public class LeaveService {

  private static final String SERVICE = "worklife-service";

  private final LeaveRequestRepository requests;
  private final WorklifeEventPublisher publisher;
  private final AuditClient audit;

  /**
   * Resolves the optional audit client at construction.
   *
   * <p>AuditConfig returns null when no table is configured, and a {@code @Bean} method returning
   * null registers a NullBean that Spring will not autowire - so a direct AuditClient field makes
   * the context fail to start. See Huo_Backend_Context.md Section 9.1.
   */
  public LeaveService(
      LeaveRequestRepository requests,
      WorklifeEventPublisher publisher,
      ObjectProvider<AuditClient> auditClientProvider) {
    this.requests = requests;
    this.publisher = publisher;
    this.audit = auditClientProvider.getIfAvailable();
  }

  /**
   * Records a new leave application and tells the approvers.
   *
   * @param principal the applicant; {@code employeeId} comes from their token, never the request
   *     body, or anyone could file leave in someone else's name
   * @param leaveType e.g. {@code ANNUAL}
   * @param from the first day, inclusive
   * @param to the last day, inclusive
   * @param reason the applicant's note
   * @return the saved request
   */
  @Transactional
  public LeaveRequest apply(
      HuvoPrincipal principal, String leaveType, LocalDate from, LocalDate to, String reason) {
    if (to.isBefore(from)) {
      throw new BusinessRuleException("The last day of leave cannot be before the first day");
    }
    Long employeeId = requireEmployeeId(principal);
    requireNoOverlap(employeeId, from, to);

    LeaveRequest request = new LeaveRequest();
    request.setEmployeeId(employeeId);
    request.setLeaveType(leaveType);
    request.setFromDate(from);
    request.setToDate(to);
    request.setReason(reason);
    request.setStatus(LeaveStatus.PENDING);
    request.setCreatedAt(OffsetDateTime.now().toLocalDateTime());

    LeaveRequest saved = requests.save(request);
    publisher.publishLeaveApplied(
        new EventTypes.LeaveAppliedPayload(employeeId, from, to, saved.getId()));
    return saved;
  }

  /**
   * Approves a pending request and publishes {@code leave.approved}.
   *
   * @param principal the approver
   * @param leaveId the request to approve
   * @param note a note for the requester
   * @return the approved request
   */
  @Transactional
  public LeaveRequest approve(HuvoPrincipal principal, Long leaveId, String note) {
    LeaveRequest request = requirePending(leaveId);
    Long employeeId = request.getEmployeeId();

    request.setStatus(LeaveStatus.APPROVED);
    request.setDecidedBy(principal.userId());
    request.setDecisionNote(note);
    request.setDecidedAt(OffsetDateTime.now().toLocalDateTime());
    LeaveRequest saved = requests.save(request);

    record(principal, "leave.approved", leaveId, note);
    publisher.publishLeaveApproved(
        new EventTypes.LeaveApprovedPayload(
            employeeId, saved.getFromDate(), saved.getToDate(), saved.getId()));
    return saved;
  }

  /**
   * Rejects a pending request and publishes {@code leave.rejected}.
   *
   * <p>attendance-service does not bind {@code leave.rejected}: a rejected application never made
   * any day {@code ON_LEAVE}, so there is nothing for it to undo. It is published for
   * notify-service to tell the requester.
   *
   * @param principal the approver
   * @param leaveId the request to reject
   * @param reason why it was rejected; required, because a refusal with no reason is not actionable
   * @return the rejected request
   */
  @Transactional
  public LeaveRequest reject(HuvoPrincipal principal, Long leaveId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessRuleException("A rejection must say why");
    }
    LeaveRequest request = requirePending(leaveId);

    request.setStatus(LeaveStatus.REJECTED);
    request.setDecidedBy(principal.userId());
    request.setDecisionNote(reason);
    request.setDecidedAt(OffsetDateTime.now().toLocalDateTime());
    LeaveRequest saved = requests.save(request);

    record(principal, "leave.rejected", leaveId, reason);
    publisher.publishLeaveRejected(
        new EventTypes.LeaveRejectedPayload(
            saved.getEmployeeId(), saved.getFromDate(), saved.getToDate(), saved.getId(), reason));
    return saved;
  }

  /** One employee's leave history. */
  @Transactional(readOnly = true)
  public List<LeaveRequest> history(Long employeeId) {
    return requests.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
  }

  /** The approver's pending queue. */
  @Transactional(readOnly = true)
  public List<LeaveRequest> pending() {
    return requests.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING);
  }

  private LeaveRequest requirePending(Long leaveId) {
    LeaveRequest request =
        requests
            .findById(leaveId)
            .orElseThrow(() -> new NotFoundException("No leave request " + leaveId));
    if (!request.getStatus().isPending()) {
      // Terminal by design: re-deciding would emit a second event for days the attendance engine
      // has
      // already been told about.
      throw new BusinessRuleException(
          "This request is already " + request.getStatus() + " and cannot be decided again");
    }
    return request;
  }

  private Long requireEmployeeId(HuvoPrincipal principal) {
    Long employeeId = principal.employeeId();
    if (employeeId == null) {
      throw new BusinessRuleException("Your account is not linked to an employee record");
    }
    return employeeId;
  }

  /**
   * Refuses a range that overlaps leave the employee already holds or is already asking for.
   *
   * <p>Both PENDING and APPROVED occupy the days. Checking only APPROVED would let one employee
   * hold several live applications for the same week, and an approver could then approve two of
   * them - publishing two {@code leave.approved} events for days attendance has already marked
   * {@code ON_LEAVE}, and counting the same leave twice against payroll. REJECTED is deliberately
   * not a conflict: a refused request frees its dates, and re-applying for them is the normal next
   * step.
   *
   * <p>Both endpoints are inclusive, so a request starting on the day an existing one ends does
   * conflict. That is not a stylistic choice - it follows from the same inclusivity the {@code
   * leave.approved} contract uses, and the two have to agree or the boundary will read one way when
   * booking and the other way when applying.
   */
  private void requireNoOverlap(Long employeeId, LocalDate from, LocalDate to) {
    List<LeaveRequest> overlapping =
        requests.findOverlapping(
            employeeId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), from, to);
    if (overlapping.isEmpty()) {
      return;
    }
    // The earliest one: the most likely to be the one the employee forgot about, and the one whose
    // dates are most useful to show them.
    LeaveRequest conflict = overlapping.get(0);
    throw new LeaveConflictException(
        conflict.getId(), conflict.getStatus(), conflict.getFromDate(), conflict.getToDate());
  }

  /**
   * Guaranteed audit: a missing client refuses the decision rather than skipping the record.
   *
   * <p>Called inside the caller's transaction, so the audit write and the decision are not
   * separable by a crash between them.
   */
  private void record(HuvoPrincipal principal, String action, Long leaveId, String note) {
    if (audit == null) {
      throw new IllegalStateException(
          "No audit client is configured, so a leave decision cannot be recorded. Refusing the "
              + "change: an approval that cannot be audited must not succeed silently. Set "
              + "AUDIT_TABLE.");
    }
    audit.record(
        AuditEntry.of(
            principal.userId(),
            principal.role(),
            action,
            "leave_request",
            String.valueOf(leaveId),
            SERVICE,
            Map.of("note", note == null ? "" : note)));
  }
}
