package com.huvo.worklife.task;

/**
 * The states a task moves through (Huvo_Backend_Context.md Section 6.1).
 *
 * <p>{@code ASSIGNED -> IN_PROGRESS -> SUBMITTED -> COMPLETED} is the progression. {@link #OVERDUE}
 * and {@link #LATE_SUBMITTED} are <em>flags</em> rather than stages: a task that went past its
 * deadline is OVERDUE, and one submitted after the deadline is LATE_SUBMITTED, but neither replaces
 * the progression. Modelling them as stages would mean a task could be both submitted and overdue,
 * which is the actual situation.
 */
public enum TaskStatus {
  ASSIGNED,
  IN_PROGRESS,
  SUBMITTED,
  COMPLETED,
  OVERDUE,
  LATE_SUBMITTED;

  /** Whether the assignee may still act on the task. */
  public boolean isOpen() {
    return this == ASSIGNED || this == IN_PROGRESS || this == OVERDUE;
  }

  /** Whether the task has been handed back and is no longer the assignee's to act on. */
  public boolean isClosed() {
    return this == SUBMITTED || this == COMPLETED || this == LATE_SUBMITTED;
  }
}
