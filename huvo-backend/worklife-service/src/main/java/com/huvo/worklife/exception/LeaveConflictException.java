package com.huvo.worklife.exception;

import java.time.LocalDate;

import com.huvo.worklife.leave.LeaveStatus;

/**
 * Leave that overlaps a request the employee already has (Huvo_Backend_Context.md Section 7).
 *
 * <p>Rendered as 409 Conflict rather than 400. The request is well-formed and the server understood
 * it - the problem is the current state of the resource, which is exactly what 409 is for, and a
 * client that retries an identical 400 has no way to know it should stop.
 *
 * <p>Carries the conflicting request's dates so the response can say <em>which</em> days are taken.
 * A bare "conflict" leaves the employee guessing, and the guess most people make - that touching
 * adjacent days is fine - is precisely the boundary this turns on.
 */
public class LeaveConflictException extends RuntimeException {

  private final Long conflictingId;
  private final LeaveStatus conflictingStatus;
  private final LocalDate conflictingFrom;
  private final LocalDate conflictingTo;

  /**
   * @param conflictingId the id of the request already covering those days
   * @param conflictingStatus whether it is still pending or already approved
   * @param conflictingFrom the first day it covers, inclusive
   * @param conflictingTo the last day it covers, inclusive
   */
  public LeaveConflictException(
      Long conflictingId,
      LeaveStatus conflictingStatus,
      LocalDate conflictingFrom,
      LocalDate conflictingTo) {
    super(
        "These dates overlap an existing "
            + conflictingStatus
            + " request for "
            + conflictingFrom
            + " to "
            + conflictingTo
            + " (both ends inclusive)");
    this.conflictingId = conflictingId;
    this.conflictingStatus = conflictingStatus;
    this.conflictingFrom = conflictingFrom;
    this.conflictingTo = conflictingTo;
  }

  public Long getConflictingId() {
    return conflictingId;
  }

  public LeaveStatus getConflictingStatus() {
    return conflictingStatus;
  }

  public LocalDate getConflictingFrom() {
    return conflictingFrom;
  }

  public LocalDate getConflictingTo() {
    return conflictingTo;
  }
}
