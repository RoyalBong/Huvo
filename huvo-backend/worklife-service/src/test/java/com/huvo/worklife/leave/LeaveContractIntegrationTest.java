package com.huvo.worklife.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.audit.AuditClient;
import com.huvo.events.EventTypes;
import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.leave.entity.LeaveRequest;
import com.huvo.worklife.leave.service.LeaveService;
import com.huvo.worklife.messaging.WorklifeEventPublisher;

/**
 * The leave path end to end through a real database, with the publisher captured.
 *
 * <p>The point of this test is the part that matters and cannot be unit tested: that the payload
 * <strong>attendance-service already consumes</strong> still arrives with the fields it expects, in
 * the meaning it expects. The producer and the consumer live in different services and different
 * repositories, so nothing at compile time stops them drifting - only a test that reads the
 * published contract back.
 *
 * <p>{@code @SpringBootTest} rather than more unit tests for the same reason attendance-service's
 * is: the bugs found there lived in the seam between the rule and the database, and a test that
 * never runs a query cannot see that seam.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class LeaveContractIntegrationTest {

  private static final Long EMPLOYEE_ID = 42L;
  private static final LocalDate FROM = LocalDate.of(2026, 9, 28); // Monday
  private static final LocalDate TO = LocalDate.of(2026, 9, 30); // Wednesday

  @Autowired private LeaveService leave;
  @Autowired private ObjectMapper objectMapper;

  /** Captured rather than stubbed to a no-op, so the published contract can be read back. */
  @MockBean private WorklifeEventPublisher publisher;

  @MockBean private AuditClient audit;

  private HuvoPrincipal manager;

  @BeforeEach
  void setUp() {
    manager = new HuvoPrincipal("mgr-1", "MANAGER", List.of(3L), EMPLOYEE_ID);
  }

  @Test
  void anApplicationStartsPending() {
    LeaveRequest saved = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
    assertThat(saved.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
  }

  @Test
  void anApplicationPublishesLeaveApplied() {
    LeaveRequest saved = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    ArgumentCaptor<EventTypes.LeaveAppliedPayload> captor =
        ArgumentCaptor.forClass(EventTypes.LeaveAppliedPayload.class);
    verify(publisher).publishLeaveApplied(captor.capture());

    assertThat(captor.getValue().employeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(captor.getValue().leaveId()).isEqualTo(saved.getId());
    assertThat(captor.getValue().from()).isEqualTo(FROM);
    assertThat(captor.getValue().to()).isEqualTo(TO);
  }

  @Test
  void approvalPublishesExactlyTheContractAttendanceConsumes() throws Exception {
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");
    long id = applied.getId();

    leave.approve(manager, id, "Enjoy");

    // Read back through the shared type attendance deserialises into. If this assertion ever has to
    // change, attendance-service has to change with it - which is the whole reason the payload is a
    // shared class rather than something each side declares for itself.
    ArgumentCaptor<EventTypes.LeaveApprovedPayload> captor =
        ArgumentCaptor.forClass(EventTypes.LeaveApprovedPayload.class);
    verify(publisher).publishLeaveApproved(captor.capture());

    String json = objectMapper.writeValueAsString(captor.getValue());
    EventTypes.LeaveApprovedPayload roundTripped =
        objectMapper.readValue(json, EventTypes.LeaveApprovedPayload.class);

    assertThat(roundTripped.employeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(roundTripped.leaveId()).isEqualTo(id);
    assertThat(roundTripped.from()).isEqualTo(FROM);
    assertThat(roundTripped.to()).isEqualTo(TO);
  }

  @Test
  void thePublishedRangeIsInclusiveAtBothEnds() {
    // Load-bearing: attendance marks every day from..to inclusive as ON_LEAVE. An exclusive `to`
    // would leave the last day of leave looking absent, and would break the employee's streak.
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    assertThat(applied.getFromDate()).isEqualTo(FROM);
    assertThat(applied.getToDate()).isEqualTo(TO);
    // Mon, Tue, Wed is three days, not two.
    assertThat(applied.dayCount()).isEqualTo(3L);
  }

  @Test
  void aSingleDayRequestIsAValidOneDayRange() {
    // The degenerate inclusive range: one day, not zero. Getting the +1 on the wrong side of the
    // check would reject this.
    LeaveRequest saved = leave.apply(manager, "SICK", FROM, FROM, "Not well");

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.dayCount()).isEqualTo(1L);
  }

  @Test
  void rejectionPublishesLeaveRejectedWithTheReason() {
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");
    long id = applied.getId();

    leave.reject(manager, id, "Quarter close, please pick another week");

    ArgumentCaptor<EventTypes.LeaveRejectedPayload> captor =
        ArgumentCaptor.forClass(EventTypes.LeaveRejectedPayload.class);
    verify(publisher).publishLeaveRejected(captor.capture());

    assertThat(captor.getValue().employeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(captor.getValue().leaveId()).isEqualTo(id);
    assertThat(captor.getValue().reason()).isEqualTo("Quarter close, please pick another week");
  }

  @Test
  void approvalIsRecordedInTheAuditTrail() {
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    leave.approve(manager, applied.getId(), "Enjoy");

    // A required audit, not a best-effort one. If this write were skipped the approval would still
    // have succeeded, invisibly.
    verify(audit).record(any());
  }

  @Test
  void aSecondApprovalOfTheSameRequestIsRefused() {
    // Terminal by design. Re-deciding would emit a second leave.approved for days attendance has
    // already marked ON_LEAVE.
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");
    leave.approve(manager, applied.getId(), "Enjoy");

    assertThatThrownBy(() -> leave.approve(manager, applied.getId(), "Sorry, changed my mind"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void aRejectionWithoutAReasonIsRefused() {
    // A refusal the requester cannot act on is not a decision.
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    assertThatThrownBy(() -> leave.reject(manager, applied.getId(), "  "))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void anApplicationEndingBeforeItStartsIsRefused() {
    assertThatThrownBy(() -> leave.apply(manager, "ANNUAL", TO, FROM, "Backwards"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void aRefusedApplicationPublishesNothing() {
    assertThatThrownBy(() -> leave.apply(manager, "ANNUAL", TO, FROM, "Backwards"))
        .isInstanceOf(BusinessRuleException.class);

    verify(publisher, never()).publishLeaveApplied(any());
  }

  @Test
  void decidingAnUnknownRequestIsANotFound() {
    assertThatThrownBy(() -> leave.approve(manager, 9999L, "ok"))
        .isInstanceOf(com.huvo.worklife.exception.NotFoundException.class);
  }

  @Test
  void thePendingQueueIsOldestFirst() {
    // Three applications in sequence; the approver queue must not starve the first.
    leave.apply(manager, "ANNUAL", FROM, FROM, "first");
    leave.apply(manager, "ANNUAL", FROM.plusDays(1), FROM.plusDays(1), "second");
    leave.apply(manager, "ANNUAL", FROM.plusDays(2), FROM.plusDays(2), "third");

    List<LeaveRequest> pending = leave.pending();

    assertThat(pending).hasSize(3);
    assertThat(pending.get(0).getReason()).isEqualTo("first");
  }

  @Test
  void aDecidedRequestLeavesThePendingQueue() {
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");
    leave.approve(manager, applied.getId(), "Enjoy");

    assertThat(leave.pending()).isEmpty();
  }

  @Test
  void historyIsNewestFirst() {
    leave.apply(manager, "ANNUAL", FROM, FROM, "first");
    leave.apply(manager, "ANNUAL", FROM.plusDays(1), FROM.plusDays(1), "second");

    List<LeaveRequest> history = leave.history(EMPLOYEE_ID);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).getReason()).isEqualTo("second");
  }

  @Test
  void theApprovalRecordsWhoDecided() {
    // The approver is captured at decision time, not looked up later: a person or role can change,
    // and the trail has to show what was true then.
    LeaveRequest applied = leave.apply(manager, "ANNUAL", FROM, TO, "Family trip");

    LeaveRequest approved = leave.approve(manager, applied.getId(), "Enjoy");

    assertThat(approved.getDecidedBy()).isEqualTo("mgr-1");
    assertThat(approved.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(approved.getDecidedAt()).isNotNull();
  }
}
