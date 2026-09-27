package com.huvo.worklife.task;

import java.time.OffsetDateTime;

/**
 * The rules that move a task between states, and mark it late (Huvo_Backend_Context.md Section
 * 6.1/6.2).
 *
 * <p>Pure, with no Spring, repository or clock, so the state machine is unit-testable directly and
 * the timestamps it needs arrive as arguments - the same discipline the attendance engine uses, for
 * the same reason: a rule that reaches for a local clock is wrong by exactly the queue delay, and
 * only on the day it goes wrong.
 */
public final class TaskTransitions {

  private TaskTransitions() {}

  /**
   * The status a task moves to when the assignee starts it.
   *
   * @param current the current status
   * @return {@link TaskStatus#IN_PROGRESS}
   * @throws IllegalStateException if the task is not open
   */
  public static TaskStatus start(TaskStatus current) {
    requireOpen(current, "start");
    return TaskStatus.IN_PROGRESS;
  }

  /**
   * The status a task moves to on submission, and whether that submission was late.
   *
   * <p>Section 6.1: a task submitted after its deadline is {@code LATE_SUBMITTED}, which is still a
   * successful submission. A task with no deadline is never late - there is nothing to be late
   * against, and treating an open-ended task as overdue would be inventing a rule.
   *
   * @param current the current status
   * @param deadline when the task was due, or null for an open-ended one
   * @param submittedAt when the assignee handed it back
   * @return {@link TaskStatus#LATE_SUBMITTED} or {@link TaskStatus#SUBMITTED}
   * @throws IllegalStateException if the task is not open
   */
  public static TaskStatus submit(
      TaskStatus current, OffsetDateTime deadline, OffsetDateTime submittedAt) {
    requireOpen(current, "submit");
    if (deadline != null && submittedAt.isAfter(deadline)) {
      return TaskStatus.LATE_SUBMITTED;
    }
    return TaskStatus.SUBMITTED;
  }

  /**
   * The status a submitted task moves to when the manager completes it.
   *
   * @param current the current status
   * @return {@link TaskStatus#COMPLETED}
   * @throws IllegalStateException if the task has not been submitted
   */
  public static TaskStatus complete(TaskStatus current) {
    if (current != TaskStatus.SUBMITTED && current != TaskStatus.LATE_SUBMITTED) {
      throw new IllegalStateException(
          "Only a submitted task can be completed, but this one is " + current);
    }
    return TaskStatus.COMPLETED;
  }

  /**
   * Whether the deadline sweep (Section 6.2) should mark this task overdue.
   *
   * <p>Only open tasks, and only those that actually have a deadline. A submitted or completed task
   * that went past its deadline is not overdue - the work is done, it was just late, which is what
   * {@link TaskStatus#LATE_SUBMITTED} records.
   *
   * @param current the current status
   * @param deadline when the task was due, or null for an open-ended one
   * @param now the sweep time
   * @return true when the task should become {@link TaskStatus#OVERDUE}
   */
  public static boolean shouldBecomeOverdue(
      TaskStatus current, OffsetDateTime deadline, OffsetDateTime now) {
    return current.isOpen() && deadline != null && now.isAfter(deadline);
  }

  private static void requireOpen(TaskStatus current, String action) {
    if (!current.isOpen()) {
      throw new IllegalStateException(
          "Cannot " + action + " a task that is " + current + "; it is no longer open");
    }
  }
}
