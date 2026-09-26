package com.huvo.attendance.engine;

import java.time.LocalDate;

/**
 * What the Section 5.2 rules engine decided, and why - the engine's entire public output.
 *
 * <p>Decisions are returned as data rather than applied here, for two reasons. The engine stays
 * pure, so every rule is unit-testable with no database, no clock and no broker (Section 1 calls
 * this the highest-risk logic in the product). And the outcome carries the reasons, so a support
 * question about why someone was marked absent is answerable from the trail rather than by
 * re-deriving it.
 *
 * @param employeeId whose day this is
 * @param date the calendar day, taken from the login timestamp, never from a local clock
 * @param outcome the status the day should hold
 * @param shift the resolved shift, or null when no roster is in effect
 * @param lateMinutes minutes past the grace period, or null when the login was not late
 * @param streak the consecutive late working days ending on this one, which spans week boundaries
 * @param counters the week-scoped counters after this evaluation
 * @param autoAbsentTriggered whether the day was escalated to ABSENT by the streak/weekly rule
 * @param skipped why no rule ran, or null when the rules ran normally
 */
public record LatenessDecision(
    Long employeeId,
    LocalDate date,
    AttendanceStatus outcome,
    ShiftWindow shift,
    Integer lateMinutes,
    int streak,
    LateCounters counters,
    boolean autoAbsentTriggered,
    SkipReason skipped) {

  /** Whether this login was late under Section 5.2 step 5. */
  public boolean isLate() {
    return outcome == AttendanceStatus.LATE || outcome == AttendanceStatus.ABSENT;
  }

  /**
   * Whether the engine did nothing, because the day was not governed by the lateness rules.
   *
   * <p>Section 5.3: an approved leave day and a company holiday both keep their own status and skip
   * the rules, while the raw login is still recorded. A second login on a day already decided also
   * skips, because only the first counts.
   */
  public boolean isSkipped() {
    return skipped != null;
  }

  /** Why the rules did not run. Null means they ran. */
  public enum SkipReason {
    /** Section 5.3: the employee has no active roster, so there is no shift to be late to. */
    NO_ACTIVE_ROSTER,
    /** Section 5.3: approved leave that day. The login is still logged. */
    ON_LEAVE,
    /**
     * Section 5.3: a company holiday. The login is still logged.
     *
     * <p>Separate from {@link #HOLIDAY}'s general notion of a non-working day so the reason a rules
     * run was skipped is explicit rather than inferred.
     */
    HOLIDAY,
    /** Not a scheduled working day at all, so there is no shift to measure lateness against. */
    NON_WORKING_DAY,
    /** Section 5.3: only the first login of a day counts; this is a later one. */
    ALREADY_EVALUATED_TODAY,
    /** The login predates the shift start, so it cannot be late. */
    BEFORE_SHIFT_START
  }

  /** A decision where the rules ran and the day is simply present. */
  public static LatenessDecision present(
      Long employeeId, LocalDate date, ShiftWindow shift, int streak, LateCounters counters) {
    return new LatenessDecision(
        employeeId, date, AttendanceStatus.PRESENT, shift, null, streak, counters, false, null);
  }

  /** A decision where the day is late, with the minutes past the grace period recorded. */
  public static LatenessDecision late(
      Long employeeId,
      LocalDate date,
      ShiftWindow shift,
      int lateMinutes,
      int streak,
      LateCounters counters,
      boolean autoAbsentTriggered) {
    return new LatenessDecision(
        employeeId,
        date,
        autoAbsentTriggered ? AttendanceStatus.ABSENT : AttendanceStatus.LATE,
        shift,
        lateMinutes,
        streak,
        counters,
        autoAbsentTriggered,
        null);
  }

  /** A decision where the rules were skipped; the counters are carried through untouched. */
  public static LatenessDecision skipped(
      Long employeeId,
      LocalDate date,
      AttendanceStatus outcome,
      SkipReason reason,
      int streak,
      LateCounters counters) {
    return new LatenessDecision(
        employeeId, date, outcome, null, null, streak, counters, false, reason);
  }
}
