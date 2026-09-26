package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A late streak that spans a week boundary.
 *
 * <p>This is the case that made the streak column in {@code late_tracker} the wrong place to keep a
 * streak. Late Thursday and late Friday, weekend, then late again the following Monday: the weekly
 * counter has reset to zero by then, so under week-scoped storage the streak restarted and the
 * three-in-a-row rule could never fire. The streak is now derived from {@code attendance_day}
 * history by the caller, so the weekend in between does not break it.
 *
 * <p>These tests drive the engine the way the consumer will: the caller walks back over scheduled
 * working days, reading {@code attendance_day}, and hands the engine what it found.
 */
class LateStreakAcrossWeekBoundaryTest {

  private static final ZoneOffset ZONE = ZoneOffset.UTC;

  /** Thursday and Friday of one week, the Saturday and Sunday after, then the next Monday. */
  private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 24);

  private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 26);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 27);
  private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 9, 28);

  private static final WorkingCalendar MON_FRI = FixedWorkingCalendar.mondayToFriday();

  private static ShiftWindow morningShift() {
    return new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), 15);
  }

  /** An hour late, so the login is unambiguously LATE. */
  private static OffsetDateTime at(LocalDate date) {
    return date.atTime(10, 0).atOffset(ZONE);
  }

  /**
   * A stand-in for the {@code attendance_day} history the consumer will read: which past dates were
   * late. A plain list of dates on purpose, so the test reads as the same derivation the production
   * code performs.
   */
  private record History(List<LocalDate> lateDates, Set<LocalDate> holidays) {

    static History lateOn(LocalDate... dates) {
      return new History(List.of(dates), Set.of());
    }

    static History lateOnWithHoliday(List<LocalDate> lateDates, LocalDate holiday) {
      return new History(lateDates, Set.of(holiday));
    }

    /**
     * Walks back from {@code today} over scheduled working days, counting consecutive late ones.
     *
     * <p>Non-working days are stepped over rather than ending the streak: a Saturday or Sunday was
     * never scheduled, so it cannot break a run of late working days. A public holiday on a weekday
     * is treated the same way - the company did not work it, so it is not a missed day either.
     *
     * @param today the day being evaluated
     * @return the streak of consecutive late scheduled working days before today
     */
    int streakBefore(LocalDate today) {
      int streak = 0;
      LocalDate cursor = today.minusDays(1);
      // Bounded by a week: a run of late days cannot be longer than one working week.
      for (int step = 0; step < 7; step++) {
        if (!MON_FRI.isWorkingDay(cursor) || holidays.contains(cursor)) {
          cursor = cursor.minusDays(1);
          continue;
        }
        if (!lateDates.contains(cursor)) {
          return streak;
        }
        streak++;
        cursor = cursor.minusDays(1);
      }
      return streak;
    }

    boolean wasPreviousWorkingDayLate(LocalDate today) {
      return streakBefore(today) > 0;
    }
  }

  private LatenessDecision evaluate(LocalDate date, History history) {
    return new LatenessEngine()
        .evaluate(
            42L,
            at(date),
            Optional.of(morningShift()),
            // The weekly counter genuinely is a fresh row on the new week: zero.
            LateCounters.zero(),
            history.streakBefore(date),
            history.wasPreviousWorkingDayLate(date),
            false,
            false,
            MON_FRI,
            ZONE);
  }

  @Test
  void theStreakFromLastWeekStillCountsOnTheMondayAfter() {
    History history = History.lateOn(THURSDAY, FRIDAY);

    LatenessDecision decision = evaluate(NEXT_MONDAY, history);

    // Thursday + Friday carried across the weekend make this the third consecutive late working
    // day, so the streak rule fires even though the new week's counter started at zero.
    assertThat(decision.streak()).isEqualTo(3);
    assertThat(decision.autoAbsentTriggered()).isTrue();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.ABSENT);
  }

  @Test
  void theWeeklyCounterIsFreshOnTheMondayEvenThoughTheStreakIsNot() {
    History history = History.lateOn(THURSDAY, FRIDAY);

    LatenessDecision decision = evaluate(NEXT_MONDAY, history);

    // The two rules read different things, and this is the proof they are independent: the
    // frequency rule is on 1 late day this week, below its threshold of 2, while the streak rule
    // is on 3 consecutive, at its threshold.
    assertThat(decision.counters().lateDaysCount()).isEqualTo(1);
    assertThat(decision.streak()).isEqualTo(3);
  }

  @Test
  void thursdayLateThenFridayLateIsStreakTwoAndDoesNotEscalate() {
    History history = History.lateOn(THURSDAY);

    LatenessDecision decision = evaluate(FRIDAY, history);

    assertThat(decision.streak()).isEqualTo(2);
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.autoAbsentTriggered()).isFalse();
  }

  @Test
  void anOnTimeThursdayBeforeALateFridayRestartsTheStreakOnMonday() {
    // The streak breaks on a scheduled working day that was not late, not on a weekend.
    History history = History.lateOn(FRIDAY);

    LatenessDecision decision = evaluate(NEXT_MONDAY, history);

    // Friday is late but Thursday was not, so the streak reaches 2 rather than 3. The weekly count
    // is 1 this week, below its threshold of 2, so nothing escalates: neither the frequency nor
    // the streak rule has tripped yet.
    assertThat(decision.streak()).isEqualTo(2);
    assertThat(decision.counters().lateDaysCount()).isEqualTo(1);
    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
  }

  @Test
  void theWeekendBetweenThemDoesNotBreakTheStreak() {
    History history = History.lateOn(THURSDAY, FRIDAY);

    // The weekend is neither late nor a break in the run.
    assertThat(MON_FRI.isWorkingDay(SATURDAY)).isFalse();
    assertThat(MON_FRI.isWorkingDay(SUNDAY)).isFalse();
    assertThat(evaluate(NEXT_MONDAY, history).streak()).isEqualTo(3);
  }

  @Test
  void aNonWorkingDayLoginIsSkippedAndLeavesTheStreakIntact() {
    History history = History.lateOn(THURSDAY, FRIDAY);

    LatenessDecision decision =
        new LatenessEngine()
            .evaluate(
                42L,
                at(SATURDAY),
                Optional.of(morningShift()),
                LateCounters.zero(),
                history.streakBefore(SATURDAY),
                history.wasPreviousWorkingDayLate(SATURDAY),
                false,
                false,
                MON_FRI,
                ZONE);

    // No shift was scheduled, so the rules do not run at all - but the streak passes through
    // unchanged, which is what lets Monday still see three.
    assertThat(decision.skipped()).isEqualTo(LatenessDecision.SkipReason.NON_WORKING_DAY);
    assertThat(decision.isLate()).isFalse();
    assertThat(decision.autoAbsentTriggered()).isFalse();
    assertThat(decision.streak()).isEqualTo(2);
  }

  @Test
  void aPublicHolidayOnAWeekdayDoesNotBreakTheStreak() {
    // Friday is a working day, but a public holiday means the company did not work it, so it is
    // stepped over like a weekend. Thursday's late login is therefore still the previous late day
    // on Monday, rather than being hidden behind a day the employee was never expected to work.
    History history = History.lateOnWithHoliday(List.of(THURSDAY), FRIDAY);

    assertThat(MON_FRI.isWorkingDay(FRIDAY)).isTrue();
    assertThat(history.streakBefore(NEXT_MONDAY)).isEqualTo(1);
    assertThat(evaluate(NEXT_MONDAY, history).streak()).isEqualTo(2);
  }

  @Test
  void aWorkedButOnTimeWeekdayDoesBreakTheStreak() {
    // The counterpart to the holiday case: a working day the company did work, on which the
    // employee was on time, is a genuine break in the run.
    History history = History.lateOn(THURSDAY);

    assertThat(history.streakBefore(NEXT_MONDAY)).isZero();
  }
}
