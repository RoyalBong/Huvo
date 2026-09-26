package com.huvo.attendance.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * A {@link WorkingCalendar} over a fixed set of ISO days of week.
 *
 * <p>Used by the unit tests and by any code path that needs a calendar without the repository. The
 * production implementation reads {@code company_working_days} instead; both satisfy the same
 * interface, so the engine cannot tell them apart - which is the point of the abstraction.
 */
public final class FixedWorkingCalendar implements WorkingCalendar {

  private final Set<DayOfWeek> workingDays;

  /** Monday to Friday, the Section 5.1 default a company would start from. */
  public static FixedWorkingCalendar mondayToFriday() {
    return new FixedWorkingCalendar(
        Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY));
  }

  /** Monday to Saturday, the six-day configuration Section 5.1 calls out explicitly. */
  public static FixedWorkingCalendar mondayToSaturday() {
    return new FixedWorkingCalendar(
        Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY));
  }

  public FixedWorkingCalendar(Set<DayOfWeek> workingDays) {
    if (workingDays.isEmpty()) {
      throw new IllegalArgumentException("A working calendar needs at least one working day");
    }
    this.workingDays = Set.copyOf(workingDays);
  }

  @Override
  public boolean isWorkingDay(LocalDate date) {
    return isWorkingWeekday(date.getDayOfWeek());
  }

  @Override
  public boolean isWorkingWeekday(DayOfWeek dayOfWeek) {
    return workingDays.contains(dayOfWeek);
  }
}
