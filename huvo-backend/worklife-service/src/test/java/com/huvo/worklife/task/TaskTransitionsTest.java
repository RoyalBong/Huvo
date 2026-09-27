package com.huvo.worklife.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * The Section 6.1 state machine, one scenario per test, named for the scenario and its outcome.
 *
 * <p>Every timestamp is passed in rather than read, because a rule that reached for a clock would
 * be wrong by exactly the queue delay on the day it matters.
 */
class TaskTransitionsTest {

  private static final OffsetDateTime NOW =
      OffsetDateTime.of(2026, 9, 28, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DEADLINE = NOW.minusHours(1);
  private static final OffsetDateTime FUTURE = NOW.plusDays(2);

  @Test
  void startingAnAssignedTaskMovesItToInProgress() {
    assertThat(TaskTransitions.start(TaskStatus.ASSIGNED)).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void startingAnOverdueTaskMovesItToInProgress() {
    // OVERDUE is a flag, not a dead end: the assignee can still pick the work up.
    assertThat(TaskTransitions.start(TaskStatus.OVERDUE)).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void startingASubmittedTaskIsRejected() {
    assertThatThrownBy(() -> TaskTransitions.start(TaskStatus.SUBMITTED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void submittingBeforeTheDeadlineIsOnTime() {
    assertThat(TaskTransitions.submit(TaskStatus.IN_PROGRESS, DEADLINE, NOW.minusDays(1)))
        .isEqualTo(TaskStatus.SUBMITTED);
  }

  @Test
  void submittingExactlyOnTheDeadlineIsOnTime() {
    // The boundary is exclusive: being late means after the deadline, not at it.
    assertThat(TaskTransitions.submit(TaskStatus.IN_PROGRESS, DEADLINE, DEADLINE))
        .isEqualTo(TaskStatus.SUBMITTED);
  }

  @Test
  void submittingOneMinuteAfterTheDeadlineIsLate() {
    assertThat(TaskTransitions.submit(TaskStatus.IN_PROGRESS, DEADLINE, DEADLINE.plusMinutes(1)))
        .isEqualTo(TaskStatus.LATE_SUBMITTED);
  }

  @Test
  void submittingATaskWithNoDeadlineIsNeverLate() {
    // An open-ended task has nothing to be late against, and inventing one would flag every
    // open-ended task on its first submission.
    assertThat(TaskTransitions.submit(TaskStatus.IN_PROGRESS, null, NOW.plusYears(1)))
        .isEqualTo(TaskStatus.SUBMITTED);
  }

  @Test
  void aLateSubmissionStillCountsAsSubmitted() {
    // LATE_SUBMITTED is a flag on a successful submission, not a failure: the work was handed in.
    assertThat(TaskTransitions.submit(TaskStatus.ASSIGNED, DEADLINE, NOW).isClosed()).isTrue();
  }

  @Test
  void submittingACompletedTaskIsRejected() {
    assertThatThrownBy(() -> TaskTransitions.submit(TaskStatus.COMPLETED, DEADLINE, NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void completingASubmittedTaskMovesItToCompleted() {
    assertThat(TaskTransitions.complete(TaskStatus.SUBMITTED)).isEqualTo(TaskStatus.COMPLETED);
  }

  @Test
  void completingALateSubmissionIsAllowed() {
    // It was still submitted; the lateness is a record, not a blocker.
    assertThat(TaskTransitions.complete(TaskStatus.LATE_SUBMITTED)).isEqualTo(TaskStatus.COMPLETED);
  }

  @Test
  void completingAnAssignedTaskIsRejected() {
    assertThatThrownBy(() -> TaskTransitions.complete(TaskStatus.ASSIGNED))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anOpenTaskPastItsDeadlineBecomesOverdue() {
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.ASSIGNED, DEADLINE, NOW)).isTrue();
  }

  @Test
  void anOpenTaskBeforeItsDeadlineIsNotOverdue() {
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.ASSIGNED, FUTURE, NOW)).isFalse();
  }

  @Test
  void aSubmittedTaskPastItsDeadlineIsNotOverdue() {
    // The work is done, it was just late - which LATE_SUBMITTED records. Re-marking it OVERDUE
    // would
    // overwrite a state that says the submission happened.
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.SUBMITTED, DEADLINE, NOW)).isFalse();
  }

  @Test
  void aCompletedTaskPastItsDeadlineIsNotOverdue() {
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.COMPLETED, DEADLINE, NOW)).isFalse();
  }

  @Test
  void anOpenTaskWithNoDeadlineIsNeverOverdue() {
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.ASSIGNED, null, NOW)).isFalse();
  }

  @Test
  void anInProgressTaskPastItsDeadlineBecomesOverdue() {
    // The sweep covers every open status, not just ASSIGNED.
    assertThat(TaskTransitions.shouldBecomeOverdue(TaskStatus.IN_PROGRESS, DEADLINE, NOW)).isTrue();
  }

  @Test
  void openStatusesAreTheOnesTheSweepMayTouch() {
    assertThat(TaskStatus.ASSIGNED.isOpen()).isTrue();
    assertThat(TaskStatus.IN_PROGRESS.isOpen()).isTrue();
    assertThat(TaskStatus.OVERDUE.isOpen()).isTrue();
    assertThat(TaskStatus.SUBMITTED.isOpen()).isFalse();
    assertThat(TaskStatus.COMPLETED.isOpen()).isFalse();
    assertThat(TaskStatus.LATE_SUBMITTED.isOpen()).isFalse();
  }
}
