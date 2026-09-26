package com.huvo.attendance.engine;

/**
 * The five outcomes an {@code attendance_day} can hold (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>These are the product's own words, not internal ones: an employee is late, absent, on leave,
 * or it is a holiday. The engine only ever produces PRESENT, LATE, ABSENT and HOLIDAY itself;
 * ON_LEAVE is set by the approved-leave path (Section 5.3) and by an admin override, both of which
 * must go through the engine rather than writing the row directly.
 */
public enum AttendanceStatus {
  PRESENT,
  LATE,
  ABSENT,
  ON_LEAVE,
  HOLIDAY;

  /**
   * Whether this outcome was produced by the lateness rules rather than by configuration.
   *
   * <p>Section 5.2 step 6 turns a day the engine already marked LATE into ABSENT, so ABSENT is an
   * automatic outcome; ON_LEAVE and HOLIDAY are not, and re-evaluating those rows would be wrong.
   */
  public boolean isAutoOutcome() {
    return this == PRESENT || this == LATE || this == ABSENT;
  }
}
