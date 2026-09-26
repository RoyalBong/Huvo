package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The Section 5.2 rules, one scenario per test, named for the scenario and its expected outcome.
 *
 * <p>This is the product's core differentiator (Section 1.1), so the names state both the input and
 * the result: a reviewer should be able to read the requirement off the test list without opening
 * any of them.
 */
class LatenessEngineTest {

  private static final ZoneOffset ZONE = ZoneOffset.UTC;

  /** 2026-09-28 is a Monday, which keeps the weekday-sensitive scenarios readable. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

  private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);

  /** The Section 5.1 default: a 09:00 shift with a 15-minute grace. */
  private static ShiftWindow morningShift() {
    return morningShift(15);
  }

  private static ShiftWindow morningShift(int graceMinutes) {
    return new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), graceMinutes);
  }

  private static LatenessEngine engine() {
    return new LatenessEngine();
  }

  private static WorkingCalendar monFri() {
    return FixedWorkingCalendar.mondayToFriday();
  }

  private static OffsetDateTime mondayAt(int hour, int minute) {
    return OffsetDateTime.of(2026, 9, 28, hour, minute, 0, 0, ZONE);
  }

  /** A login on an ordinary working Monday, with the rules actually running. */
  private LatenessDecision evaluate(
      OffsetDateTime loginAt, int graceMinutes, LateCounters counters, boolean previousDayWasLate) {
    return evaluate(
        loginAt, graceMinutes, counters, previousDayWasLate ? 1 : 0, previousDayWasLate);
  }

  private LatenessDecision evaluate(
      OffsetDateTime loginAt,
      int graceMinutes,
      LateCounters counters,
      int currentStreak,
      boolean previousDayWasLate) {
    return engine()
        .evaluate(
            42L,
            loginAt,
            Optional.of(morningShift(graceMinutes)),
            counters,
            currentStreak,
            previousDayWasLate,
            false,
            false,
            monFri(),
            ZONE);
  }

  // --- the grace-period boundary, the rule most easily got wrong ---

  @Test
  void loginFifteenMinutesBeforeShiftStartIsPresent() {
    assertThat(evaluate(mondayAt(8, 45), 15, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void loginExactlyOnShiftStartIsPresent() {
    assertThat(evaluate(mondayAt(9, 0), 15, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void loginExactlyOnGracePeriodBoundaryIsNotLate() {
    // 09:00 + 15 = 09:15, and the product's rule is "15+ minutes counts as late", so the
    // comparison must be strictly greater than the grace period. Using >= would mark this late.
    LatenessDecision decision = evaluate(mondayAt(9, 15), 15, LateCounters.zero(), false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(decision.isLate()).isFalse();
  }

  @Test
  void loginOneMinutePastTheGracePeriodBoundaryIsLate() {
    LatenessDecision decision = evaluate(mondayAt(9, 16), 15, LateCounters.zero(), false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.lateMinutes()).isEqualTo(16);
  }

  @Test
  void loginAnHourPastShiftStartIsLateByTheFullDelta() {
    LatenessDecision decision = evaluate(mondayAt(10, 0), 15, LateCounters.zero(), false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.lateMinutes()).isEqualTo(60);
  }

  @Test
  void aLongerGracePeriodOnTheSameLoginIsNotLate() {
    // The same 09:30 login is late under a 15-minute grace and on time under 45, so the rule
    // reads the shift's own allowance rather than a constant.
    assertThat(evaluate(mondayAt(9, 30), 15, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.LATE);
    assertThat(evaluate(mondayAt(9, 30), 45, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void loginBeforeShiftStartIsPresentEvenHoursEarly() {
    // Section 5.2 only calls a login late for arriving after the shift start. An early arrival is
    // not a lateness problem, and a large negative delta must not count as "very late".
    LatenessDecision decision = evaluate(mondayAt(3, 0), 15, LateCounters.zero(), false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(decision.lateMinutes()).isNull();
  }

  @Test
  void aZeroGracePeriodMakesTheShiftStartOnTimeAndTheNextMinuteLate() {
    assertThat(evaluate(mondayAt(9, 0), 0, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.PRESENT);
    assertThat(evaluate(mondayAt(9, 1), 0, LateCounters.zero(), false).outcome())
        .isEqualTo(AttendanceStatus.LATE);
  }
}
