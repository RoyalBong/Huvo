package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The Section 5.3 edge cases. Each is a day where the lateness rules must <em>not</em> run, or must
 * run exactly once, and each is the kind of rule that gets lost in a refactor because nothing looks
 * broken until payroll does.
 */
class LatenessEngineEdgeCaseTest {

  private static final ZoneOffset ZONE = ZoneOffset.UTC;

  /** 2026-10-03 is a Saturday: a holiday under Mon-Fri, ordinary work under Mon-Sat. */
  private static final OffsetDateTime SATURDAY = OffsetDateTime.of(2026, 10, 3, 10, 0, 0, 0, ZONE);

  private static ShiftWindow morningShift() {
    return new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), 15);
  }

  private static LatenessEngine engine() {
    return new LatenessEngine();
  }

  /** An hour-late Monday login, with the two flags Section 5.3 varies. */
  private static LatenessDecision onMonday(
      LateCounters counters, boolean onLeave, boolean alreadyDecided, boolean previousDayWasLate) {
    return onMonday(
        counters, onLeave, alreadyDecided, previousDayWasLate ? 1 : 0, previousDayWasLate);
  }

  private static LatenessDecision onMonday(
      LateCounters counters,
      boolean onLeave,
      boolean alreadyDecided,
      int currentStreak,
      boolean previousDayWasLate) {
    return engine()
        .evaluate(
            42L,
            OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZONE),
            Optional.of(morningShift()),
            counters,
            currentStreak,
            previousDayWasLate,
            alreadyDecided,
            onLeave,
            FixedWorkingCalendar.mondayToFriday(),
            ZONE);
  }

  @Test
  void anApprovedLeaveDayKeepsOnLeaveAndSkipsTheRules() {
    LatenessDecision decision = onMonday(LateCounters.zero(), true, false, false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ON_LEAVE);
    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.ON_LEAVE);
    assertThat(decision.isLate()).isFalse();
  }

  @Test
  void anApprovedLeaveDayDoesNotEscalateToAbsentNoMatterHowLongTheStreak() {
    // The leave check runs before the counters, so a long streak cannot auto-absent a day the
    // employee was legitimately away for.
    LatenessDecision decision = onMonday(new LateCounters(9), true, false, true);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ON_LEAVE);
    assertThat(decision.autoAbsentTriggered()).isFalse();
  }

  @Test
  void anApprovedLeaveDayLeavesTheCountersUntouched() {
    LatenessDecision decision = onMonday(new LateCounters(2), true, false, true);

    assertThat(decision.counters()).isEqualTo(new LateCounters(2));
  }

  /**
   * A login at a specific instant with the default flags, for the cases that vary the date or the
   * calendar rather than the counters.
   */
  private static LatenessDecision evaluateAt(
      OffsetDateTime loginAt, ShiftWindow shift, WorkingCalendar calendar) {
    return engine()
        .evaluate(
            42L,
            loginAt,
            Optional.ofNullable(shift),
            LateCounters.zero(),
            0,
            false,
            false,
            false,
            calendar,
            ZONE);
  }

  @Test
  void aCompanyHolidayOnASaturdayIsMarkedHolidayAndSkipsTheRules() {
    LatenessDecision decision =
        evaluateAt(SATURDAY, morningShift(), FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.HOLIDAY);
    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.NON_WORKING_DAY);
  }

  @Test
  void aSaturdayIsOrdinaryWorkUnderTheSixDayConfiguration() {
    // The same Saturday, evaluated under a Mon-Sat company, so the calendar is genuinely
    // consulted rather than a hardcoded weekday check.
    ShiftWindow shift = new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(15, 0), 15);

    LatenessDecision decision =
        evaluateAt(SATURDAY, shift, FixedWorkingCalendar.mondayToSaturday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
  }

  @Test
  void aSecondLoginOnAnAlreadyDecidedDayDoesNotReRunTheRules() {
    // Section 5.3: only the first login of a day counts. The raw login is still recorded by the
    // caller; this is only about not re-evaluating it.
    LatenessDecision decision = onMonday(LateCounters.zero(), false, true, false);

    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.ALREADY_EVALUATED_TODAY);
    assertThat(decision.isLate()).isFalse();
  }

  @Test
  void aSecondLoginOnAnAlreadyDecidedDayDoesNotDoubleCountTheStreak() {
    LatenessDecision decision = onMonday(new LateCounters(1), false, true, true);

    // Without this, two late logins on one day could trip the weekly auto-absent threshold and
    // mark an employee absent for a single late morning.
    assertThat(decision.counters().lateDaysCount()).isEqualTo(1);
    assertThat(decision.autoAbsentTriggered()).isFalse();
  }

  @Test
  void aSecondLoginOnAnAlreadyDecidedDayDoesNotEscalateToAbsent() {
    LatenessDecision decision = onMonday(new LateCounters(1), false, true, true);

    assertThat(decision.outcome()).isNotEqualTo(AttendanceStatus.ABSENT);
  }

  @Test
  void leaveOutranksAHolidayWhenBothApply() {
    // Approved leave on a company holiday is leave, because that is what was approved for the
    // individual. The order of these two checks is a product decision, so it is pinned.
    LatenessDecision decision = onSaturdayWithLeave();

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ON_LEAVE);
  }

  /** A Saturday login that is also covered by approved leave. */
  private static LatenessDecision onSaturdayWithLeave() {
    return engine()
        .evaluate(
            42L,
            SATURDAY,
            Optional.of(morningShift()),
            LateCounters.zero(),
            0,
            false,
            false,
            true,
            FixedWorkingCalendar.mondayToFriday(),
            ZONE);
  }

  @Test
  void aLoginWithNoActiveRosterIsPresentRatherThanLate() {
    // No shift means there is no lateness rule to break; inventing one would be wrong.
    LatenessDecision decision =
        evaluateAt(
            OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZONE),
            null,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.NO_ACTIVE_ROSTER);
  }

  @Test
  void aShiftWithANegativeGracePeriodIsRejected() {
    assertThatThrownBy(
            () -> new ShiftWindow(1L, "Bad", LocalTime.of(9, 0), LocalTime.of(17, 0), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyDecisionCarriesTheEmployeeAndDateItWasMadeFor() {
    LatenessDecision decision = onMonday(LateCounters.zero(), false, false, false);

    assertThat(decision.employeeId()).isEqualTo(42L);
    assertThat(decision.date()).isEqualTo(LocalDate.of(2026, 9, 28));
  }
}
