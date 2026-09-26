package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WorkingCalendarTest {

  // One clean Monday-to-Sunday week, so the weekday arithmetic below is readable. 2026-09-28 is
  // a Monday; 2026-10-03 the Saturday and 2026-10-04 the Sunday of that same week.
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
  private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 29);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 2);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
  private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);

  /** The Monday after the weekend, which starts the next working week. */
  private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 5);

  @Test
  void mondayToFridayTreatsTheWeekendAsNonWorking() {
    FixedWorkingCalendar calendar = FixedWorkingCalendar.mondayToFriday();

    assertThat(calendar.isWorkingDay(MONDAY)).isTrue();
    assertThat(calendar.isWorkingDay(SATURDAY)).isFalse();
    assertThat(calendar.isWorkingDay(SUNDAY)).isFalse();
  }

  @Test
  void mondayToSaturdayTreatsSaturdayAsWorking() {
    // Section 5.1 lists Mon-Sat as a supported configuration, so the default must not be
    // baked into the engine.
    FixedWorkingCalendar calendar = FixedWorkingCalendar.mondayToSaturday();

    assertThat(calendar.isWorkingDay(SATURDAY)).isTrue();
    assertThat(calendar.isWorkingDay(SUNDAY)).isFalse();
  }

  @Test
  void aNonWorkingDayBelongsToNoWeekAndIsRejected() {
    // A weekend login skips the lateness rules entirely (Section 5.3), so it never reaches a
    // counter and never needs a week. Rejecting it here removes the question entirely rather than
    // inventing an answer for it.
    FixedWorkingCalendar calendar = FixedWorkingCalendar.mondayToFriday();

    assertThatThrownBy(() -> calendar.weekStartFor(SATURDAY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a working day");
    assertThatThrownBy(() -> calendar.weekStartFor(SUNDAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWorkingSaturdayDoesBelongToAWeekUnderTheSixDayConfiguration() {
    // The counterpart: the same Saturday is fine once the company works it, which is what makes
    // the rejection above about the calendar rather than about the date.
    assertThat(FixedWorkingCalendar.mondayToSaturday().weekStartFor(SATURDAY)).isEqualTo(MONDAY);
  }

  @Test
  void theMondayAfterAWeekendStartsTheNextWeek() {
    // The boundary case: the first working day after a weekend begins a new week's row.
    assertThat(FixedWorkingCalendar.mondayToFriday().weekStartFor(NEXT_MONDAY))
        .isEqualTo(NEXT_MONDAY);
  }

  @Test
  void weekStartIsTheFirstWorkingDayOfTheRunNotTheDateItself() {
    // Tuesday belongs to the week that started on the Monday, so its week row is the Monday's.
    // This is what keeps one week's counters together across Mon-Fri.
    FixedWorkingCalendar calendar = FixedWorkingCalendar.mondayToFriday();

    assertThat(calendar.weekStartFor(TUESDAY)).isEqualTo(MONDAY);
    assertThat(calendar.weekStartFor(FRIDAY)).isEqualTo(MONDAY);
  }

  @Test
  void weekStartIsMondayForEveryDayOfAWorkingWeek() {
    // One week row for the whole working week, which is what keeps its counters together.
    FixedWorkingCalendar calendar = FixedWorkingCalendar.mondayToFriday();

    for (LocalDate day = MONDAY; !day.isAfter(FRIDAY); day = day.plusDays(1)) {
      assertThat(calendar.weekStartFor(day)).isEqualTo(MONDAY);
    }
  }

  @Test
  void aSoleWorkingDayCalendarHasThatDayAsTheOnlyWeek() {
    // The degenerate case: with only Monday worked, Monday is its own week and no other day can
    // be, because none of them are working days.
    FixedWorkingCalendar onlyMonday = new FixedWorkingCalendar(Set.of(DayOfWeek.MONDAY));

    assertThat(onlyMonday.weekStartFor(MONDAY)).isEqualTo(MONDAY);
    assertThatThrownBy(() -> onlyMonday.weekStartFor(FRIDAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void previousWorkingDayOfAMondayIsThePrecedingFriday() {
    // Section 5.2 step 5c resets the consecutive counter when yesterday was not late; on a
    // Monday the "yesterday" for a Mon-Fri company is the previous Friday, not Sunday.
    assertThat(FixedWorkingCalendar.mondayToFriday().previousWorkingDay(MONDAY))
        .isEqualTo(FRIDAY.minusWeeks(1));
  }

  @Test
  void previousWorkingDayOfATuesdayIsMonday() {
    assertThat(FixedWorkingCalendar.mondayToFriday().previousWorkingDay(TUESDAY)).isEqualTo(MONDAY);
  }

  @Test
  void previousWorkingDayOfSaturdayIsFriday() {
    assertThat(FixedWorkingCalendar.mondayToFriday().previousWorkingDay(SATURDAY))
        .isEqualTo(FRIDAY);
  }

  @Test
  void previousWorkingDayIsAlwaysFoundWithinOneWeekCycle() {
    // Any calendar with at least one working weekday has one inside a seven-day window, so the
    // lookup always terminates with an answer rather than returning null. A Friday under a
    // Monday-only calendar finds the Monday four days earlier, still inside the same week.
    FixedWorkingCalendar onlyMonday = new FixedWorkingCalendar(Set.of(DayOfWeek.MONDAY));

    assertThat(onlyMonday.previousWorkingDay(FRIDAY)).isEqualTo(MONDAY);
  }

  @Test
  void aCalendarWithNoWorkingDaysIsRejectedAtConstruction() {
    // Better to fail loudly here than to throw IllegalStateException from weekStartFor later.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new FixedWorkingCalendar(Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
