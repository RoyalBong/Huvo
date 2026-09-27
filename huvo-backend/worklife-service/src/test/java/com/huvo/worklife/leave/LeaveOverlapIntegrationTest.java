package com.huvo.worklife.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.audit.AuditClient;
import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.LeaveConflictException;
import com.huvo.worklife.leave.entity.LeaveRequest;
import com.huvo.worklife.leave.service.LeaveService;
import com.huvo.worklife.messaging.WorklifeEventPublisher;

/**
 * The overlap rule on {@code apply()}: an employee cannot hold two requests covering the same day.
 *
 * <p>Separate from {@code LeaveContractIntegrationTest} because this is a different question. That
 * one asks "does the published contract match what attendance reads"; this one asks "is the range
 * allowed at all", including the inclusive/inclusive boundary where a new request starts on the
 * very day an existing one ends.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class LeaveOverlapIntegrationTest {

  private static final Long EMPLOYEE_ID = 42L;

  /** 2026-09-28 is a Monday, so the ranges are unambiguous calendar days. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

  private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 29);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 1);

  @Autowired private LeaveService leave;

  @MockBean private WorklifeEventPublisher publisher;
  @MockBean private AuditClient audit;

  private HuvoPrincipal employee;

  @BeforeEach
  void setUp() {
    employee = new HuvoPrincipal("u-42", "EMPLOYEE", List.of(3L), EMPLOYEE_ID);
  }

  @Test
  void anIdenticalSecondRequestIsRefused() {
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "same again"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestOverlappingAPendingOneIsRefused() {
    // Not just approved leave: two live applications for the same week is exactly how an approver
    // ends up approving two of them, and the same days then get marked ON_LEAVE twice.
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "pending one");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", TUESDAY, THURSDAY, "overlapping"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestOverlappingAnApprovedOneIsRefused() {
    LeaveRequest approved = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "approved one");
    leave.approve(employee, approved.getId(), "ok");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", TUESDAY, TUESDAY, "overlapping"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestStartingTheDayAnExistingOneEndsIsRefused() {
    // The pinned boundary. Both endpoints are inclusive, so sharing a single day is an overlap.
    // Getting this wrong either way is a real defect: too strict and employees cannot book
    // back-to-back weeks, too loose and a day is claimed twice and marked ON_LEAVE twice.
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "Mon-Wed");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", WEDNESDAY, THURSDAY, "Wed-Thu"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestStartingTheDayAfterAnExistingOneEndsIsAllowed() {
    // The other side of the same boundary, and the case that fails when the comparison is off by
    // one
    // in the other direction. Wed then Thu share no day, so this must succeed.
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "Mon-Wed");

    LeaveRequest next = leave.apply(employee, "ANNUAL", THURSDAY, THURSDAY, "Thu only");

    assertThat(next.getId()).isNotNull();
    assertThat(next.getFromDate()).isEqualTo(THURSDAY);
  }

  @Test
  void aRequestEndingTheDayAnExistingOneStartsIsRefused() {
    // The same edge approached from the other side, because the predicate is not symmetric in the
    // source and an asymmetric implementation would only show up here. Mon-Tue ends on the same day
    // Tue-Wed starts, so they share Tuesday and must conflict.
    leave.apply(employee, "ANNUAL", TUESDAY, WEDNESDAY, "Tue-Wed");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, TUESDAY, "Mon-Tue"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestEndingTheDayBeforeAnExistingOneStartsIsAllowed() {
    // And the mirror of the mirror. Mon alone and Tue-Wed share nothing, so this must succeed - a
    // rule one day too strict would reject this, and it is the case that makes back-to-back booking
    // work at all.
    leave.apply(employee, "ANNUAL", TUESDAY, WEDNESDAY, "Tue-Wed");

    LeaveRequest before = leave.apply(employee, "ANNUAL", MONDAY, MONDAY, "Mon only");

    assertThat(before.getId()).isNotNull();
  }

  @Test
  void aRequestFullyEnclosingAnExistingOneIsRefused() {
    leave.apply(employee, "ANNUAL", TUESDAY, TUESDAY, "one day");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "the whole week"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRequestFullyInsideAnExistingOneIsRefused() {
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "the whole week");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", TUESDAY, TUESDAY, "one day inside"))
        .isInstanceOf(LeaveConflictException.class);
  }

  @Test
  void aRejectedRequestDoesNotBlockReapplyingForTheSameDates() {
    // A refusal frees its days. Blocking here would leave the employee unable to re-ask for leave
    // the
    // approver turned down for a reason that has since changed.
    LeaveRequest rejected = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first try");
    leave.reject(employee, rejected.getId(), "Quarter close");

    LeaveRequest retry = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "asking again");

    assertThat(retry.getId()).isNotNull();
    assertThat(retry.getId()).isNotEqualTo(rejected.getId());
  }

  @Test
  void anotherEmployeesLeaveDoesNotBlock() {
    HuvoPrincipal colleague = new HuvoPrincipal("u-99", "EMPLOYEE", List.of(3L), 99L);
    leave.apply(colleague, "ANNUAL", MONDAY, WEDNESDAY, "theirs");

    LeaveRequest mine = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "mine");

    assertThat(mine.getId()).isNotNull();
  }

  @Test
  void theConflictCarriesTheConflictingRequestsDates() {
    // The employee has to be told which days are taken, not just that there is a clash. Without the
    // dates the only way to find out is to go and look at their other request.
    LeaveRequest existing = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", TUESDAY, THURSDAY, "clash"))
        .isInstanceOfSatisfying(
            LeaveConflictException.class,
            ex -> {
              assertThat(ex.getConflictingId()).isEqualTo(existing.getId());
              assertThat(ex.getConflictingStatus()).isEqualTo(LeaveStatus.PENDING);
              assertThat(ex.getConflictingFrom()).isEqualTo(MONDAY);
              assertThat(ex.getConflictingTo()).isEqualTo(WEDNESDAY);
              // The message names the days too, so the plain-text body is useful on its own.
              assertThat(ex.getMessage()).contains("2026-09-28").contains("2026-09-30");
            });
  }

  @Test
  void theConflictReportsAnApprovedRequestAsApproved() {
    // The status matters: "you already have leave" and "you already asked" call for different
    // responses, and collapsing them into one message loses that.
    LeaveRequest approved = leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first");
    leave.approve(employee, approved.getId(), "ok");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "clash"))
        .isInstanceOfSatisfying(
            LeaveConflictException.class,
            ex -> assertThat(ex.getConflictingStatus()).isEqualTo(LeaveStatus.APPROVED));
  }

  @Test
  void aRefusedApplicationIsNotSaved() {
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "clash"))
        .isInstanceOf(LeaveConflictException.class);

    // The check runs before the insert, so the approver queue does not fill with duplicates nobody
    // will ever act on.
    assertThat(leave.pending()).hasSize(1);
  }

  @Test
  void aRefusedApplicationPublishesNothing() {
    leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "first");

    assertThatThrownBy(() -> leave.apply(employee, "ANNUAL", MONDAY, WEDNESDAY, "clash"))
        .isInstanceOf(LeaveConflictException.class);

    // notify-service must not tell an approver about a request that does not exist.
    verify(publisher, times(1)).publishLeaveApplied(any());
  }

  @Test
  void nonOverlappingRangesStackFreely() {
    // The rule must not be so broad that ordinary sequential leave stops working.
    LeaveRequest first = leave.apply(employee, "ANNUAL", MONDAY, TUESDAY, "Mon-Tue");
    LeaveRequest second = leave.apply(employee, "ANNUAL", WEDNESDAY, THURSDAY, "Wed-Thu");

    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(leave.pending()).hasSize(2);
  }
}
