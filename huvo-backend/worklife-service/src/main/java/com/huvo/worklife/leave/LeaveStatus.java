package com.huvo.worklife.leave;

/**
 * A leave request and where it is in its life (Huvo_Backend_Context.md Section 7).
 *
 * <p>{@code PENDING -> APPROVED} or {@code PENDING -> REJECTED}, and no further. A decided request
 * is final: re-opening a rejected request would be a new request, so approving it would emit a
 * second {@code leave.approved} for days the attendance engine has already been told about.
 */
public enum LeaveStatus {
  PENDING,
  APPROVED,
  REJECTED;

  /** Whether this request is still awaiting a decision. */
  public boolean isPending() {
    return this == PENDING;
  }
}
