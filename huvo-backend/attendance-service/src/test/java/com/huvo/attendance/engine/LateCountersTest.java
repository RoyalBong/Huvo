package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The week-scoped counter, on its own.
 *
 * <p>The streak that used to live here has moved into the engine and is derived from {@code
 * attendance_day} history, because a streak is not a property of a week. See {@link
 * LateStreakAcrossWeekBoundaryTest} for that half.
 */
class LateCountersTest {

  @Test
  void aFreshWeekStartsAtZero() {
    assertThat(LateCounters.zero().lateDaysCount()).isZero();
  }

  @Test
  void forNewWeekMatchesZero() {
    assertThat(LateCounters.forNewWeek()).isEqualTo(LateCounters.zero());
  }

  @Test
  void oneLateDayIncrementsTheWeeklyCount() {
    assertThat(LateCounters.zero().incremented().lateDaysCount()).isEqualTo(1);
  }

  @Test
  void theWeeklyCountAccumulatesWithinAWeek() {
    assertThat(LateCounters.zero().incremented().incremented().lateDaysCount()).isEqualTo(2);
  }

  @Test
  void oneLateDayIsBelowTheFrequencyThreshold() {
    assertThat(LateCounters.zero().hitsWeeklyThreshold()).isFalse();
  }

  @Test
  void exactlyTwoLateDaysHitsTheFrequencyThreshold() {
    // The threshold is inclusive, per Section 5.2 step 6's ">= 2".
    assertThat(new LateCounters(1).hitsWeeklyThreshold()).isFalse();
    assertThat(new LateCounters(2).hitsWeeklyThreshold()).isTrue();
  }

  @Test
  void theWeeklyCountKeepsItsValueAfterAnAutoAbsentTrigger() {
    // The Section 5.2 step 6 reset applies to the streak, which the engine owns. The weekly count
    // is untouched, so a support query can still see how many late days the week held.
    assertThat(new LateCounters(2).afterAutoAbsentTrigger().lateDaysCount()).isEqualTo(2);
  }

  @Test
  void theCounterCarriesNoStreakStateAtAll() {
    // A structural assertion, and the one that would have caught the original design error: the
    // record has exactly one component, so a week-scoped row cannot hold a cross-week value.
    var components = LateCounters.class.getRecordComponents();

    assertThat(components).hasSize(1);
    assertThat(components[0].getName()).isEqualTo("lateDaysCount");
  }
}
