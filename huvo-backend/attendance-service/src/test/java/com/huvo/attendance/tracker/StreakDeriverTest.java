package com.huvo.attendance.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.engine.AttendanceStatus;
import com.huvo.attendance.engine.FixedWorkingCalendar;
import com.huvo.attendance.engine.WorkingCalendar;

/**
 * The streak derivation, which produces the {@code currentStreak} and {@code previousDayWasLate}
 * the engine is handed.
 *
 * <p><b>The highest-risk code in the service.</b> The engine is proven correct against the inputs
 * {@code LateStreakAcrossWeekBoundaryTest} builds by hand; these tests check that the production
 * derivation produces the same inputs from history. A regression here would mark the wrong people
 * absent while every engine test still passed, so each rule the engine assumes is pinned here
 * independently.
 */
class StreakDeriverTest {

  private static final WorkingCalendar MON_FRI = FixedWorkingCalendar.mondayToFriday();
  private static final WorkingCalendar MON_SAT = FixedWorkingCalendar.mondayToSaturday();

  /** 2026-09-24 is a Thursday, 09-25 a Friday, 09-26/27 a weekend, 09-28 the next Monday. */
  private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 24);

  private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 26);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 23);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 22);
  private static final LocalDate PREVIOUS_MONDAY = LocalDate.of(2026, 9, 21);
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

  private static AttendanceDay day(LocalDate date, AttendanceStatus status) {
    AttendanceDay attendanceDay = new AttendanceDay();
    attendanceDay.setDate(date);
    attendanceDay.setStatus(status);
    return attendanceDay;
  }

  private static Map<LocalDate, AttendanceStatus> historyOf(AttendanceDay... days) {
    return StreakDeriver.indexByDate(List.of(days));
  }

  @Test
  void anEmptyHistoryIsAStreakOfZero() {
    assertThat(StreakDeriver.streakBefore(Map.of(), MONDAY, MON_FRI)).isZero();
  }

  @Test
  void aLatePreviousWorkingDayIsAStreakOfOne() {
    Map<LocalDate, AttendanceStatus> history = historyOf(day(FRIDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(1);
  }

  @Test
  void theWeekendIsSkippedRatherThanBreakingTheStreak() {
    // The rule most easily got wrong: Saturday and Sunday have no rows at all, so a derivation that
    // treated a missing row as "on time" would return 0 here and the Monday escalation would never
    // fire.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.LATE), day(FRIDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(2);
  }

  @Test
  void aStreakCrossingAWeekBoundaryIsCountedInFull() {
    // The scenario the week-scoped column could never produce.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.LATE), day(FRIDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(2);
    // And the caller adds today's login to make three.
    assertThat(StreakDeriver.wasPreviousScheduledDayLate(history, MONDAY, MON_FRI)).isTrue();
  }

  @Test
  void anOnTimeWorkingDayBreaksTheStreak() {
    // Friday late, Thursday on time: the walk reaches Thursday, sees PRESENT, and stops.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.PRESENT), day(FRIDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(1);
  }

  @Test
  void aPublicHolidayDoesNotBreakTheStreak() {
    // A holiday is a working weekday the company did not work, so it is stepped over like a weekend
    // rather than counted as an on-time day.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.LATE), day(FRIDAY, AttendanceStatus.HOLIDAY));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(1);
  }

  @Test
  void approvedLeaveDoesNotBreakTheStreak() {
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.LATE), day(FRIDAY, AttendanceStatus.ON_LEAVE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(1);
  }

  @Test
  void anAutoAbsentDayCountsAsALateDay() {
    // ABSENT is how the engine escalates a late day, so it must continue the streak rather than
    // being read as an absence that ends it.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(day(THURSDAY, AttendanceStatus.ABSENT), day(FRIDAY, AttendanceStatus.ABSENT));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(2);
  }

  @Test
  void aMissingRowOnAWorkingDayStopsTheWalkRatherThanClaimingZero() {
    // No attendance_day row for Friday at all. The conservative answer is the streak up to that
    // point: an incomplete history must never manufacture an auto-absent escalation.
    Map<LocalDate, AttendanceStatus> history = historyOf(day(THURSDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(1);
  }

  @Test
  void aStreakCannotExceedTheLookbackBound() {
    // Every day for a fortnight marked late. The walk is bounded, so it terminates and reports what
    // it found rather than counting indefinitely: seven calendar days back from a Monday is five
    // working days, because the weekend is stepped over inside the bound.
    AttendanceDay[] many = new AttendanceDay[14];
    for (int i = 0; i < many.length; i++) {
      many[i] = day(MONDAY.minusDays(i + 1), AttendanceStatus.LATE);
    }

    int streak = StreakDeriver.streakBefore(historyOf(many), MONDAY, MON_FRI);

    assertThat(streak).isLessThanOrEqualTo(StreakDeriver.MAX_LOOKBACK_DAYS).isPositive();
  }

  @Test
  void theLookbackIsIrrespectiveOfHowMuchHistoryExists() {
    // One week of late days and a fortnight of them must give the same answer, which is what "the
    // bound is a guard, not a business limit" means in practice. Both fixtures cover the same five
    // working days back from the Monday; the second simply has older rows as well.
    AttendanceDay[] oneWeek = {
      day(FRIDAY, AttendanceStatus.LATE),
      day(THURSDAY, AttendanceStatus.LATE),
      day(WEDNESDAY, AttendanceStatus.LATE),
      day(TUESDAY, AttendanceStatus.LATE),
      day(PREVIOUS_MONDAY, AttendanceStatus.LATE)
    };
    AttendanceDay[] twoWeeks = new AttendanceDay[14];
    for (int i = 0; i < twoWeeks.length; i++) {
      twoWeeks[i] = day(MONDAY.minusDays(i + 1), AttendanceStatus.LATE);
    }

    assertThat(StreakDeriver.streakBefore(historyOf(oneWeek), MONDAY, MON_FRI))
        .isEqualTo(StreakDeriver.streakBefore(historyOf(twoWeeks), MONDAY, MON_FRI));
  }

  @Test
  void aWorkedSaturdayIsCountedUnderTheSixDayConfiguration() {
    // The calendar is genuinely consulted: the same Friday and Saturday a Mon-Fri company would
    // skip
    // are real working days here, and the streak differs.
    Map<LocalDate, AttendanceStatus> history =
        historyOf(
            day(THURSDAY, AttendanceStatus.LATE),
            day(FRIDAY, AttendanceStatus.LATE),
            day(SATURDAY, AttendanceStatus.LATE));

    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_SAT)).isEqualTo(3);
    assertThat(StreakDeriver.streakBefore(history, MONDAY, MON_FRI)).isEqualTo(2);
  }

  @Test
  void wasPreviousScheduledDayLateIsFalseWhenTheStreakIsZero() {
    Map<LocalDate, AttendanceStatus> history = historyOf(day(FRIDAY, AttendanceStatus.PRESENT));

    assertThat(StreakDeriver.wasPreviousScheduledDayLate(history, MONDAY, MON_FRI)).isFalse();
  }

  @Test
  void indexingKeepsTheFirstRowForADuplicatedDate() {
    // A duplicate cannot happen given the unique key, but if one ever did the derivation must be
    // deterministic rather than depending on repository ordering. The merge keeps the first entry,
    // so the streak below reflects the LATE row rather than the PRESENT one.
    Map<LocalDate, AttendanceStatus> indexed =
        StreakDeriver.indexByDate(
            List.of(day(FRIDAY, AttendanceStatus.LATE), day(FRIDAY, AttendanceStatus.PRESENT)));

    assertThat(indexed).hasSize(1);
    assertThat(StreakDeriver.streakBefore(indexed, MONDAY, MON_FRI)).isEqualTo(1);
  }
}
