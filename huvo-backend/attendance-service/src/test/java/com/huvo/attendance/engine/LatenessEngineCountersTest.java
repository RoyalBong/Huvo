package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The counter arithmetic and the auto-absent escalation (Huvo_Backend_Context.md Section 5.2 steps
 * 5c and 6). Split from {@link LatenessEngineTest} so the escalation rules can be read as a list on
 * their own - they are the part most likely to be changed.
 */
class LatenessEngineCountersTest {

  private static final ZoneOffset ZONE = ZoneOffset.UTC;

  private static ShiftWindow morningShift() {
    return new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), 15);
  }

  /** An hour past a 09:00 shift start, so the login is late whatever the counters say. */
  private static OffsetDateTime lateMonday() {
    return OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZONE);
  }

  private static LatenessDecision evaluate(LateCounters counters, boolean previousDayWasLate) {
    return evaluate(counters, previousDayWasLate ? 1 : 0, previousDayWasLate);
  }

  private static LatenessDecision evaluate(
      LateCounters counters, int currentStreak, boolean previousDayWasLate) {
    return new LatenessEngine()
        .evaluate(
            42L,
            lateMonday(),
            Optional.of(morningShift()),
            counters,
            currentStreak,
            previousDayWasLate,
            false,
            false,
            FixedWorkingCalendar.mondayToFriday(),
            ZONE);
  }

  @Test
  void firstLateDayOfAWeekIsLateNotAbsent() {
    LatenessDecision decision = evaluate(LateCounters.zero(), false);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.counters().lateDaysCount()).isEqualTo(1);
    assertThat(decision.streak()).isEqualTo(1);
  }

  @Test
  void secondLateDayInTheSameWeekTriggersAutoAbsent() {
    // Section 5.2 step 6's frequency rule: two or more late days this week.
    LatenessDecision decision = evaluate(new LateCounters(1), 1, true);

    assertThat(decision.autoAbsentTriggered()).isTrue();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ABSENT);
  }

  @Test
  void thirdConsecutiveLateDayTriggersAutoAbsentInAFreshWeek() {
    // The streak rule on its own: a brand new week whose counters start at zero, reached only
    // because the streak is carried across the boundary from attendance_day history. Under the old
    // week-scoped storage this was unreachable - the new week reset the streak to 0 and the
    // frequency rule had always fired first.
    LatenessDecision decision = evaluate(LateCounters.zero(), 2, true);

    assertThat(decision.autoAbsentTriggered()).isTrue();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(decision.counters().lateDaysCount()).isEqualTo(1);
  }

  @Test
  void autoAbsentKeepsTheWeeklyCountBecauseTheFrequencyRuleStillReadsIt() {
    LatenessDecision decision = evaluate(new LateCounters(1), 1, true);

    assertThat(decision.counters().lateDaysCount()).isEqualTo(2);
  }

  @Test
  void aLateDayAfterAnOnTimeDayRestartsTheStreakAtOne() {
    // previousDayWasLate=false is the Section 5.2 step 5c reset: the streak goes to 1, not to 3,
    // even though the caller had derived 2.
    LatenessDecision decision = evaluate(LateCounters.zero(), 2, false);

    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.streak()).isEqualTo(1);
  }

  @Test
  void anOnTimeLoginDoesNotTouchTheCounters() {
    LateCounters before = new LateCounters(3);

    LatenessDecision decision =
        new LatenessEngine()
            .evaluate(
                42L,
                OffsetDateTime.of(2026, 9, 28, 9, 5, 0, 0, ZONE),
                Optional.of(morningShift()),
                before,
                1,
                true,
                false,
                false,
                FixedWorkingCalendar.mondayToFriday(),
                ZONE);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(decision.counters()).isEqualTo(before);
  }

  @Test
  void aStreakOfTwoDoesNotEscalateOnTheSecondDay() {
    // Proving the threshold is on the third, and that the second day is genuinely LATE rather
    // than absent: this is the state a late streak is in just before it triggers.
    LatenessDecision decision = evaluate(LateCounters.zero(), 1, true);

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.streak()).isEqualTo(2);
  }

  @Test
  void aStreakOfFourAlreadyEscalatedLongerAgoButTheStreakIsReported() {
    // The decision still reports the streak it evaluated, so a support question about a day can
    // be answered from the decision itself.
    LatenessDecision decision = evaluate(LateCounters.zero(), 4, true);

    assertThat(decision.streak()).isEqualTo(5);
    assertThat(decision.autoAbsentTriggered()).isTrue();
  }
}
