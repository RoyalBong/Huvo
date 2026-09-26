package com.huvo.attendance.engine;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The Section 5.2 lateness rules, as a pure function of its inputs (Huvo_Backend_Context.md Section
 * 5.2). No Spring, no repository, no clock, no broker.
 *
 * <p>Everything the rules need arrives as an argument - the login timestamp, the resolved shift,
 * the calendar, the counters, whether the day is already decided or on leave. The engine never
 * calls {@code now()}, which is the whole point: Section 5.2 computes the delta against {@code
 * login_timestamp}, so a rule that reached for the local clock would silently drift by the
 * processing delay every time the queue is backed up.
 *
 * <p>Isolated in its own package (Section 5: "a dedicated rules-engine package ... not scattered
 * across controllers") because it is the highest-risk logic in the product. The boundary between it
 * and the rest of the service is this class plus {@link LatenessDecision}: the caller resolves
 * state and persists the outcome, and the engine only decides.
 */
public class LatenessEngine {

  /**
   * Evaluates one login.
   *
   * @param employeeId the employee who logged in
   * @param loginTimestamp when they logged in, as produced by identity-service. This, never a local
   *     clock, is the input to the lateness calculation.
   * @param shift the employee's active shift, or empty when no roster is in effect
   * @param counters their weekly counters for the week this login falls in. Week-scoped only; the
   *     streak is not stored here, because a week boundary must not reset it
   * @param currentStreak how many consecutive scheduled working days before this one were late,
   *     derived by the caller from {@code attendance_day} history. Pass 0 when they are not
   * @param previousDayWasLate whether the previous scheduled working day was late, which decides
   *     whether the streak continues or restarts at 1 (Section 5.2 step 5c)
   * @param alreadyDecided whether today's row already exists and has been decided - a second login
   *     on the same day is recorded but must not re-run the rules (Section 5.3)
   * @param onLeave whether approved leave covers this day (Section 5.3)
   * @param calendar the company working calendar
   * @param zone the zone shift start times are expressed in
   * @return the decision, including why the rules were skipped when they were
   */
  public LatenessDecision evaluate(
      Long employeeId,
      OffsetDateTime loginTimestamp,
      Optional<ShiftWindow> shift,
      LateCounters counters,
      int currentStreak,
      boolean previousDayWasLate,
      boolean alreadyDecided,
      boolean onLeave,
      WorkingCalendar calendar,
      ZoneId zone) {

    var date = loginTimestamp.atZoneSameInstant(zone).toLocalDate();

    // Section 5.3: leave and holiday outrank the rules, and in this order - a day that is both
    // approved leave and a company holiday is leave, because that is what was approved for the
    // individual. Both keep the login in login_event; only the rules are skipped.
    if (onLeave) {
      return LatenessDecision.skipped(
          employeeId,
          date,
          AttendanceStatus.ON_LEAVE,
          LatenessDecision.SkipReason.ON_LEAVE,
          currentStreak,
          counters);
    }
    if (!calendar.isWorkingDay(date)) {
      // A non-working day is skipped for the same reason as leave and a holiday: no shift was
      // scheduled, so there is nothing to be late to. The streak passes through untouched, which
      // is what lets it span a weekend intact.
      return LatenessDecision.skipped(
          employeeId,
          date,
          AttendanceStatus.HOLIDAY,
          LatenessDecision.SkipReason.NON_WORKING_DAY,
          currentStreak,
          counters);
    }

    // Section 5.3: multiple logins in a day - only the first counts. The raw row was already
    // written by the caller before this method ran; here we simply leave the day alone.
    if (alreadyDecided) {
      return LatenessDecision.skipped(
          employeeId,
          date,
          AttendanceStatus.PRESENT,
          LatenessDecision.SkipReason.ALREADY_EVALUATED_TODAY,
          currentStreak,
          counters);
    }

    if (shift.isEmpty()) {
      // No roster means no shift to be late to. Marking such a login late would be inventing a
      // rule the product does not have.
      return LatenessDecision.skipped(
          employeeId,
          date,
          AttendanceStatus.PRESENT,
          LatenessDecision.SkipReason.NO_ACTIVE_ROSTER,
          currentStreak,
          counters);
    }

    ShiftWindow window = shift.get();
    OffsetDateTime shiftStart = shiftStartOn(loginTimestamp, window, zone);
    Duration delta = Duration.between(shiftStart, loginTimestamp);

    // The streak after this login: 0 if the previous scheduled working day was not late, otherwise
    // the caller's derived streak plus this one. Derived from attendance_day history by the caller,
    // never read from the week-scoped row, so it survives a week boundary.
    int streak = previousDayWasLate ? currentStreak + 1 : 1;

    // Section 5.2 step 6, and the product's "15+ minutes counts as late": the comparison is
    // strictly greater than the grace period, so a login exactly on the boundary is on time.
    // A login before the shift starts is never late, whatever the grace period says.
    if (!delta.isNegative() && delta.toMinutes() > window.graceMinutes()) {
      LateCounters updated = counters.incremented();
      // Either pattern escalates: two late days in one week (frequency), or three in a row
      // (streak). They are independent, so each has to be able to fire on its own.
      boolean autoAbsent = updated.hitsWeeklyThreshold() || streak >= 3;
      LateCounters finalCounters = autoAbsent ? updated.afterAutoAbsentTrigger() : updated;
      return LatenessDecision.late(
          employeeId, date, window, (int) delta.toMinutes(), streak, finalCounters, autoAbsent);
    }

    return LatenessDecision.present(employeeId, date, window, streak, counters);
  }

  /**
   * The shift's start time on the login's own calendar day, in the given zone.
   *
   * <p>A shift belongs to a day, so a night shift's 22:00 start is combined with the login's date
   * rather than today's date - otherwise a login at 01:00 the morning after a 22:00 start would be
   * measured against the wrong evening entirely.
   */
  private static OffsetDateTime shiftStartOn(
      OffsetDateTime loginTimestamp, ShiftWindow window, ZoneId zone) {
    return loginTimestamp
        .atZoneSameInstant(zone)
        .toLocalDate()
        .atTime(window.startTime())
        .atZone(zone)
        .toOffsetDateTime();
  }
}
