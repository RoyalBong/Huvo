package com.huvo.attendance.engine;

/**
 * /** The week-scoped lateness counters for one employee in one week (Huvo_Backend_Context.md
 * Section 5.1).
 *
 * <p>Deliberately carries <em>only</em> the weekly count. Section 5.2 step 5c's {@code
 * consecutive_late_days_count} used to live here too, and that was wrong: a streak is a property of
 * an employee's recent history, not of a week. Storing it in a week-keyed row meant a new week
 * silently reset it to 0, so the three-consecutive-day rule could never fire - the
 * two-late-days-in-a-week rule always escalated first and reset the streak before it reached three.
 * The two rules exist to catch different patterns, frequency and streak, and the streak one was
 * dead code.
 *
 * <p>The streak is now derived by the caller from {@code attendance_day} history (was the previous
 * scheduled working day also late?) and supplied to the engine as {@code previousDayWasLate}, so it
 * spans week boundaries and non-working days with no week-scoped storage at all.
 *
 * @param lateDaysCount late days so far this week
 */
public record LateCounters(int lateDaysCount) {

  public static LateCounters zero() {
    return new LateCounters(0);
  }

  /**
   * The counters after another late day.
   *
   * <p>Only the weekly count changes here. The streak is the caller's to carry, precisely because
   * it outlives the week this row belongs to.
   *
   * @return the updated weekly counters
   */
  public LateCounters incremented() {
    return new LateCounters(lateDaysCount + 1);
  }

  /**
   * Whether Section 5.2 step 6's <em>frequency</em> condition holds: two or more late days this
   * week.
   *
   * <p>The streak half of that condition deliberately lives in {@link LatenessEngine}, which needs
   * the derived streak to evaluate it. Keeping only the week-scoped half here makes it obvious that
   * the two rules are independent rather than one being a fallback for the other.
   */
  public boolean hitsWeeklyThreshold() {
    return lateDaysCount >= 2;
  }

  /**
   * Section 5.2 step 6 resets the <em>streak</em> to 0 after triggering, so the employee does not
   * auto-absent every subsequent day. That reset belongs to the engine, which owns the streak; the
   * weekly count is left alone because it is the input to the frequency rule. Returning {@code
   * this} states that at the call site.
   */
  public LateCounters afterAutoAbsentTrigger() {
    return this;
  }

  /** Convenience for the caller building a fresh row for a new week. */
  public static LateCounters forNewWeek() {
    return zero();
  }
}
