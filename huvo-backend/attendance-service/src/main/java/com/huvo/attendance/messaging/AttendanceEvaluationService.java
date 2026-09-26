package com.huvo.attendance.messaging;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.attendance.calendar.DatabaseWorkingCalendar;
import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.day.repository.AttendanceDayRepository;
import com.huvo.attendance.engine.AttendanceStatus;
import com.huvo.attendance.engine.LateCounters;
import com.huvo.attendance.engine.LatenessDecision;
import com.huvo.attendance.engine.LatenessEngine;
import com.huvo.attendance.engine.ShiftWindow;
import com.huvo.attendance.engine.WorkingCalendar;
import com.huvo.attendance.shift.repository.RosterRepository;
import com.huvo.attendance.shift.repository.ShiftRepository;
import com.huvo.attendance.tracker.StreakDeriver;
import com.huvo.attendance.tracker.entity.LateTracker;
import com.huvo.attendance.tracker.repository.LateTrackerRepository;

/**
 * Assembles the inputs the rules engine needs, and persists what it decides (Section 5.2).
 *
 * <p>Deliberately the only place that reads the database to build an engine input. The engine
 * decides; this resolves, derives and persists. Keeping resolution out of the engine is what lets
 * it stay pure - and it is also why {@link StreakDeriver} has its own tests: a mistake in the
 * inputs would pass every engine test while producing the wrong attendance.
 *
 * <p>The whole evaluation runs in one transaction so the {@code attendance_day} row, its {@code
 * late_tracker} counter and the decision cannot disagree with each other.
 */
@Component
public class AttendanceEvaluationService {

  /** How much history to load for the streak walk - a streak cannot exceed a working week. */
  private static final int STREAK_LOOKBACK_DAYS = StreakDeriver.MAX_LOOKBACK_DAYS + 1;

  private final AttendanceDayRepository days;
  private final LateTrackerRepository trackers;
  private final RosterRepository rosters;
  private final ShiftRepository shifts;
  private final WorkingCalendar calendar;
  private final LatenessEngine engine;
  private final ZoneId zone;

  public AttendanceEvaluationService(
      AttendanceDayRepository days,
      LateTrackerRepository trackers,
      RosterRepository rosters,
      ShiftRepository shifts,
      DatabaseWorkingCalendar calendar,
      LatenessEngine engine,
      ZoneId zone) {
    this.days = days;
    this.trackers = trackers;
    this.rosters = rosters;
    this.shifts = shifts;
    this.calendar = calendar;
    this.engine = engine;
    this.zone = zone;
  }

  /**
   * Evaluates one login and persists the outcome, without publishing anything.
   *
   * <p>Publishing is the caller's job so a broker failure cannot roll back attendance state that
   * has already been decided - the same reasoning as the publishers in identity-service.
   *
   * @param employeeId whose day to evaluate
   * @param loginAt when the credentials were verified, from the event payload
   * @return the engine's decision, after it has been applied
   */
  @Transactional
  public LatenessDecision evaluateAndPersist(Long employeeId, OffsetDateTime loginAt) {
    LocalDate date = loginAt.atZoneSameInstant(zone).toLocalDate();
    Optional<AttendanceDay> today = days.findByEmployeeIdAndDate(employeeId, date);
    boolean alreadyDecided = today.isPresent();
    Optional<ShiftWindow> shift = resolveShift(employeeId, date);

    // The two inputs the engine cannot derive for itself, and the ones most likely to be wrong:
    // the streak walks back over scheduled working days only, skipping weekends and any day the
    // employee was not expected in.
    //
    // The range is exclusive of today on purpose - the streak measures the days *before* this
    // login. Today's own row is read separately below, because leave is a property of today.
    Map<LocalDate, AttendanceStatus> history =
        StreakDeriver.indexByDate(
            days.findByEmployeeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                employeeId, date.minusDays(STREAK_LOOKBACK_DAYS), date));
    int currentStreak = StreakDeriver.streakBefore(history, date, calendar);
    boolean previousDayWasLate = currentStreak > 0;

    LateCounters counters = countersFor(employeeId, date);

    LatenessDecision decision =
        engine.evaluate(
            employeeId,
            loginAt,
            shift,
            counters,
            currentStreak,
            previousDayWasLate,
            alreadyDecided,
            isOnLeave(today),
            calendar,
            zone);

    persist(decision, counters, loginAt);
    return decision;
  }

  /**
   * Whether approved leave covers today (Section 5.3).
   *
   * <p>Driven by the {@code attendance_day} row that the {@code leave.approved} consumer writes, or
   * by an admin override. Read from today's row rather than the streak history, because the history
   * range deliberately excludes today - a separate query here is what keeps that exclusion from
   * silently turning this flag off.
   */
  private static boolean isOnLeave(Optional<AttendanceDay> today) {
    return today.map(day -> day.getStatus() == AttendanceStatus.ON_LEAVE).orElse(false);
  }

  /**
   * Resolves the employee's active roster to its shift (Section 5.2 step 2), or empty when they
   * have no assignment in force on the day.
   */
  private Optional<ShiftWindow> resolveShift(Long employeeId, LocalDate date) {
    return rosters.findActiveOn(employeeId, date).stream()
        .findFirst()
        .map(roster -> shifts.findById(roster.getShiftId()).orElse(null))
        .map(
            shift ->
                new ShiftWindow(
                    shift.getId(),
                    shift.getName(),
                    shift.getStartTime(),
                    shift.getEndTime(),
                    shift.getGraceMinutes()));
  }

  /**
   * The employee's counter for the week this date falls in, or a zero counter when they have not
   * been late yet this week.
   *
   * <p>Only reached for a working day, since {@code weekStartFor} rejects a non-working one - and
   * the engine skips those before it matters.
   */
  private LateCounters countersFor(Long employeeId, LocalDate date) {
    if (!calendar.isWorkingDay(date)) {
      return LateCounters.zero();
    }
    return trackers
        .findByEmployeeIdAndWeekStartDate(employeeId, calendar.weekStartFor(date))
        .map(tracker -> new LateCounters(tracker.getLateDaysCount()))
        .orElseGet(LateCounters::zero);
  }

  /**
   * Writes the decision: the day's outcome, and the weekly counter when the day was late.
   *
   * <p>A skipped decision creates the day if it is missing but changes no counter, so a weekend
   * login still records that the employee was in without touching a weekly count.
   */
  private void persist(LatenessDecision decision, LateCounters before, OffsetDateTime loginAt) {
    AttendanceDay day = findOrCreate(decision, loginAt);
    day.setStatus(decision.outcome());
    day.setAutoMarked(decision.autoAbsentTriggered());
    days.save(day);

    if (decision.isLate() && calendar.isWorkingDay(decision.date())) {
      recordLate(decision, before);
    }
  }

  /**
   * The day's existing row, or a new one.
   *
   * <p>{@code first_login_at} is stamped only when the row is created, from the login's own
   * timestamp. A second login on the same day re-saves the row but must not move it, because
   * Section 5.1 defines it as the first login of the day.
   */
  private AttendanceDay findOrCreate(LatenessDecision decision, OffsetDateTime loginAt) {
    LocalDateTime loginTime = loginAt.atZoneSameInstant(zone).toLocalDateTime();
    return days.findByEmployeeIdAndDate(decision.employeeId(), decision.date())
        .orElseGet(
            () -> {
              AttendanceDay fresh = new AttendanceDay();
              fresh.setEmployeeId(decision.employeeId());
              fresh.setDate(decision.date());
              fresh.setFirstLoginAt(loginTime);
              return fresh;
            });
  }

  /**
   * Increments the weekly counter. The streak is not stored - it is re-derived from {@code
   * attendance_day} on the next login, which is what lets it cross a week boundary.
   */
  private void recordLate(LatenessDecision decision, LateCounters before) {
    LocalDate weekStart = calendar.weekStartFor(decision.date());
    LateTracker tracker =
        trackers
            .findByEmployeeIdAndWeekStartDate(decision.employeeId(), weekStart)
            .orElseGet(() -> new LateTracker(decision.employeeId(), weekStart));
    tracker.setLateDaysCount(before.lateDaysCount() + 1);
    trackers.save(tracker);
  }
}
