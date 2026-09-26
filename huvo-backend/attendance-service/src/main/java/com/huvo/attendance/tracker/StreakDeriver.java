package com.huvo.attendance.tracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.engine.AttendanceStatus;
import com.huvo.attendance.engine.WorkingCalendar;

/**
 * Derives the current late streak and whether the previous day was late, from {@code
 * attendance_day} history.
 *
 * <p><b>This is the piece that can feed the engine bad input.</b> {@link
 * com.huvo.attendance.engine.LatenessEngine} is proven correct against the inputs {@code
 * LateStreakAcrossWeekBoundaryTest} constructs; this class is responsible for producing those
 * inputs correctly from the database. A streak that counted weekends, or that treated a public
 * holiday as a missed day, would make the engine's streak rule fire on the wrong employees while
 * every engine test still passed.
 *
 * <p>Three rules, all of which must hold:
 *
 * <ol>
 *   <li>Walk back over <b>scheduled working days only</b> - a Saturday or Sunday is skipped, never
 *       counted as a non-late day that breaks the run.
 *   <li>A day the employee was <b>not expected in</b> does not break the streak either. HOLIDAY and
 *       ON_LEAVE mean the company did not expect them, so they are skipped like a weekend.
 *   <li>A day that was worked and came out PRESENT or LATE <b>does</b> end or continue the run.
 * </ol>
 *
 * <p>Pure and static, with its collaborators passed in, so the derivation is unit-testable without
 * a database.
 */
public final class StreakDeriver {

  /**
   * How far back the walk can go. A streak of consecutive late scheduled working days cannot exceed
   * a full working week, so seven days always terminates; the bound guards against a calendar with
   * no working days at all rather than encoding a business limit.
   */
  public static final int MAX_LOOKBACK_DAYS = 7;

  private StreakDeriver() {}

  /**
   * Counts consecutive late days immediately before {@code today}.
   *
   * <p>A day counts as late only when it was LATE or escalated to ABSENT - both are outcomes of
   * being late that day, and an auto-absent day is emphatically a late day.
   *
   * @param history the employee's days keyed by date
   * @param today the day being evaluated, excluded from the walk
   * @param calendar the company's working calendar
   * @return the streak length, 0 when the previous scheduled working day was on time
   */
  public static int streakBefore(
      Map<LocalDate, AttendanceStatus> history, LocalDate today, WorkingCalendar calendar) {
    int streak = 0;
    LocalDate cursor = today.minusDays(1);
    for (int step = 0; step < MAX_LOOKBACK_DAYS; step++) {
      // Three distinct cases, and conflating any two of them is wrong:
      //
      //   1. not a working day      -> step over it; it cannot break a run of late days
      //   2. working day, no row     -> STOP; we have no evidence about it
      //   3. working day, with a row -> judge it
      //
      // Cases 1 and 2 look alike (both have "nothing to count") and were originally handled by one
      // predicate, which silently turned a gap into a skip-through: the walk stepped over a
      // scheduled day it knew nothing about and went on to count the late days behind it. That
      // inflated a streak from unverified history, and the engine then escalated the employee to
      // ABSENT and notified their manager on that basis. Keeping them apart is the whole fix.
      if (!calendar.isWorkingDay(cursor)) {
        // A weekend. Stepping over is correct: it was never scheduled, so it is not a missed day.
        cursor = cursor.minusDays(1);
        continue;
      }

      AttendanceStatus status = history.get(cursor);
      if (status == null) {
        // A scheduled working day with no attendance_day row: incomplete history. Stop rather than
        // look past the gap, because everything behind it is unverified. An incomplete history must
        // never manufacture an auto-absent escalation.
        return streak;
      }
      if (status == AttendanceStatus.HOLIDAY || status == AttendanceStatus.ON_LEAVE) {
        // A working weekday the company did not work. Like a weekend, it is stepped over rather
        // than treated as an on-time day, so a holiday or approved leave cannot break a streak.
        cursor = cursor.minusDays(1);
        continue;
      }
      if (status == AttendanceStatus.LATE || status == AttendanceStatus.ABSENT) {
        // ABSENT included: the engine escalates it from a late day, so it is one.
        streak++;
        cursor = cursor.minusDays(1);
        continue;
      }
      // PRESENT: worked, and on time. A genuine break in the run.
      return streak;
    }
    return streak;
  }

  /**
   * Whether the previous scheduled working day was late, which is what Section 5.2 step 5c uses to
   * choose between continuing the streak and restarting it.
   *
   * @param history the employee's days keyed by date
   * @param today the day being evaluated
   * @param calendar the company's working calendar
   * @return true when the streak continues
   */
  public static boolean wasPreviousScheduledDayLate(
      Map<LocalDate, AttendanceStatus> history, LocalDate today, WorkingCalendar calendar) {
    return streakBefore(history, today, calendar) > 0;
  }

  /**
   * Indexes a history list by date, so the walk's per-day lookups are O(1) rather than a scan.
   *
   * @param history the employee's days
   * @return a date to status map
   */
  public static Map<LocalDate, AttendanceStatus> indexByDate(List<AttendanceDay> history) {
    return history.stream()
        .collect(Collectors.toMap(AttendanceDay::getDate, AttendanceDay::getStatus, (a, b) -> a));
  }
}
