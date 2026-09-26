package com.huvo.attendance.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.attendance.calendar.DatabaseWorkingCalendar;
import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.day.repository.AttendanceDayRepository;
import com.huvo.attendance.engine.AttendanceStatus;
import com.huvo.attendance.engine.LatenessDecision;
import com.huvo.attendance.engine.LatenessEngine;
import com.huvo.attendance.login.repository.LoginEventRepository;
import com.huvo.attendance.shift.entity.Roster;
import com.huvo.attendance.shift.entity.Shift;
import com.huvo.attendance.shift.repository.RosterRepository;
import com.huvo.attendance.shift.repository.ShiftRepository;
import com.huvo.attendance.tracker.repository.LateTrackerRepository;

/**
 * The assembled data path, end to end through a real database: a login in, a decided day out.
 *
 * <p>Deliberately a {@code @SpringBootTest} rather than more unit tests. The engine and the streak
 * derivation are each proven in isolation, but both bugs found in this service lived in the seam
 * between them and the database - a history query that excluded today, and a walk that skipped gaps
 * - and neither could be caught by a test that never runs a query. This one would have caught the
 * first.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AttendanceEvaluationIntegrationTest {

  private static final Long EMPLOYEE_ID = 42L;

  /** 2026-09-28 is a Monday, so a late login is unambiguous and not a weekend. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

  private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 24);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);

  @Autowired private AttendanceEvaluationService evaluator;
  @Autowired private AttendanceDayRepository days;
  @Autowired private LateTrackerRepository trackers;
  @Autowired private LoginEventRepository logins;
  @Autowired private RosterRepository rosters;
  @Autowired private ShiftRepository shifts;
  @Autowired private LatenessEngine engine;
  @Autowired private DatabaseWorkingCalendar calendar;

  @BeforeEach
  void seedShift() {
    Shift morning = shifts.save(new Shift("Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), 15));
    rosters.save(new Roster(EMPLOYEE_ID, morning.getId(), MONDAY.minusMonths(1), null));
  }

  private java.time.OffsetDateTime loginAt(int hour, int minute) {
    return MONDAY.atTime(hour, minute).atOffset(java.time.ZoneOffset.UTC);
  }

  private AttendanceDay day(LocalDate date, AttendanceStatus status) {
    AttendanceDay attendanceDay = new AttendanceDay();
    attendanceDay.setEmployeeId(EMPLOYEE_ID);
    attendanceDay.setDate(date);
    attendanceDay.setStatus(status);
    return attendanceDay;
  }

  @Test
  void anOnTimeLoginRecordsTheDayAsPresent() {
    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(9, 5));

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, MONDAY))
        .hasValueSatisfying(day -> assertThat(day.getStatus()).isEqualTo(AttendanceStatus.PRESENT));
  }

  @Test
  void aLateLoginMarksTheDayLateAndIncrementsTheWeeklyCounter() {
    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(
            trackers.findByEmployeeIdAndWeekStartDate(EMPLOYEE_ID, calendar.weekStartFor(MONDAY)))
        .hasValueSatisfying(tracker -> assertThat(tracker.getLateDaysCount()).isEqualTo(1));
  }

  @Test
  void firstLoginAtIsStampedFromTheLoginTimestampNotTheClock() {
    evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(9, 42));

    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, MONDAY))
        .hasValueSatisfying(
            day -> assertThat(day.getFirstLoginAt()).isEqualTo(MONDAY.atTime(9, 42)));
  }

  @Test
  void aSecondLoginDoesNotMoveFirstLoginAtOrDoubleCountTheDay() {
    // Both logins are late, so the weekly counter exists and a double count would be visible.
    evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));
    evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(11, 0));

    // Section 5.3: only the first login counts, so first_login_at must stay put and the weekly
    // counter must still read 1.
    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, MONDAY))
        .hasValueSatisfying(
            day -> assertThat(day.getFirstLoginAt()).isEqualTo(MONDAY.atTime(10, 0)));
    assertThat(
            trackers.findByEmployeeIdAndWeekStartDate(EMPLOYEE_ID, calendar.weekStartFor(MONDAY)))
        .hasValueSatisfying(tracker -> assertThat(tracker.getLateDaysCount()).isEqualTo(1));
  }

  @Test
  void approvedLeaveSkipsTheRulesForTheDayItCovers() {
    // The §5.3 path the leave.approved consumer feeds. This is the assertion that would have caught
    // the history query excluding today: with the leave row stored for today, the service must read
    // today's row specifically to see it.
    days.save(day(MONDAY, AttendanceStatus.ON_LEAVE));

    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ON_LEAVE);
    assertThat(decision.isLate()).isFalse();
    assertThat(decision.autoAbsentTriggered()).isFalse();
    // The day still exists, which is what §5.3 requires: the login happened, the rules did not run.
    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, MONDAY)).isPresent();
  }

  @Test
  void aWeekendLoginIsRecordedButDecidesNothing() {
    LatenessDecision decision =
        evaluator.evaluateAndPersist(
            EMPLOYEE_ID, SATURDAY.atTime(10, 0).atOffset(java.time.ZoneOffset.UTC));

    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.NON_WORKING_DAY);
    assertThat(decision.isLate()).isFalse();
    // Section 5.1: the fact table exists independently of the rules, so a day the rules skip is
    // still a day the employee showed up.
    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, SATURDAY)).isPresent();
  }

  @Test
  void anEmployeeWithNoRosterIsPresentRatherThanLate() {
    rosters.deleteAll();

    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void theStreakIsReadFromRealHistoryNotFromTheWeekRow() {
    // Thursday and Friday late, then Monday late. The weekly row is fresh at zero, so this only
    // escalates if the streak came from the attendance_day history - the reason the column was
    // removed from late_tracker.
    days.save(day(THURSDAY, AttendanceStatus.LATE));
    days.save(day(FRIDAY, AttendanceStatus.LATE));

    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    assertThat(decision.streak()).isEqualTo(3);
    assertThat(decision.autoAbsentTriggered()).isTrue();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ABSENT);
    // autoMarked is what tells a support query this was the engine, not an admin.
    assertThat(days.findByEmployeeIdAndDate(EMPLOYEE_ID, MONDAY))
        .hasValueSatisfying(AttendanceDay::isAutoMarked);
  }

  @Test
  void aGapInHistoryDoesNotEscalate() {
    // The gap bug, through the real query path: Friday has no row, so the streak must stop there
    // rather than skipping to Thursday and inflating the count.
    days.save(day(THURSDAY, AttendanceStatus.LATE));

    LatenessDecision decision = evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
  }

  @Test
  void theEngineNeverWritesToTheLoginFactTable() {
    evaluator.evaluateAndPersist(EMPLOYEE_ID, loginAt(10, 0));

    // Only the consumer writes there. Asserting zero makes that boundary explicit rather than
    // implied by the absence of code.
    assertThat(logins.count()).isZero();
  }
}
