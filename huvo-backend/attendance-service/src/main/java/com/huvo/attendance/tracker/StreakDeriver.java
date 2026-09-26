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
      if (!isExpectedIn(cursor, history, calendar)) {
        cursor = cursor.minusDays(1);
        continue;
      }
      AttendanceStatus status = history.get(cursor);
      if (status == null) {
        // A scheduled working day with no attendance_day row at all: no evidence, so it cannot be
        // called late. Treating the absence as "on time" would silently break a streak, so the walk
        // stops instead. An incomplete history must never manufacture an auto-absent escalation.
        return streak;
      }
      if (status == AttendanceStatus.LATE || status == AttendanceStatus.ABSENT) {
        streak++;
        cursor = cursor.minusDays(1);
        continue;
      }
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
   * Whether the employee was expected in on a date, which is what decides if it counts towards or
   * breaks a streak.
   *
   * <p>Needs both the calendar and the recorded status, because a public holiday falls on a working
   * weekday: the calendar says it was scheduled, the row says the company did not work it. Only
   * PRESENT, LATE and ABSENT mean "expected in" - ABSENT included, because the engine escalates it
   * from a late day and the row may already have been auto-marked.
   */
  private static boolean isExpectedIn(
      LocalDate date, Map<LocalDate, AttendanceStatus> history, WorkingCalendar calendar) {
    if (!calendar.isWorkingDay(date)) {
      return false;
    }
    AttendanceStatus status = history.get(date);
    return status == AttendanceStatus.PRESENT
        || status == AttendanceStatus.LATE
        || status == AttendanceStatus.ABSENT;
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
