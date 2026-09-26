package com.huvo.attendance.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The company's working calendar (Huvo_Backend_Context.md Sections 5.2 step 7, 5.3).
 *
 * <p>Exists so neither the engine nor the week-boundary job hardcodes "Monday to Friday". Section
 * 5.1 makes the working set configurable per company, and a six-day week is explicitly called out
 * as a supported configuration.
 *
 * <p>An interface rather than a value so the rules engine stays free of Spring and of the
 * repository underneath it - the engine is the highest-risk logic in the product and its unit tests
 * supply a plain implementation.
 */
public interface WorkingCalendar {

  /**
   * @param date the day to test
   * @return true when the company works that weekday
   */
  boolean isWorkingDay(LocalDate date);

  /**
   * The first working day of the week that {@code date} belongs to.
   *
   * <p>Defined as the start of the <em>contiguous run of working days</em> containing the date,
   * which is what makes the Mon-Fri and Mon-Sat configurations both correct without a hardcoded
   * weekday. For Mon-Fri: Saturday, Sunday and the following Monday all resolve to that Monday, so
   * a weekend login shares one week row and is not counted as starting a new week. For Mon-Sat,
   * Saturday is its own week's start and Sunday rolls forward to it.
   *
   * <p>Note it walks back over working days, not to an arbitrary Monday - asking "when did this
   * week start" is a question about the company's calendar, not about the Gregorian one.
   *
   * @param date any date
   * @return the anchor of the week that {@code date} belongs to
   */
  default LocalDate weekStartFor(LocalDate date) {
    if (!isWorkingDay(date)) {
      throw new IllegalArgumentException(
          date
              + " is not a working day, so it belongs to no attendance week; a login on it skips the"
              + " lateness rules entirely (Section 5.3)");
    }
    return startOfRunContaining(date);
  }

  /**
   * The first day of the contiguous run of working days that {@code date} is part of.
   *
   * @param date a working day
   * @return the first day of its run of working days
   */
  private LocalDate startOfRunContaining(LocalDate date) {
    LocalDate start = date;
    // Seven steps is a full week, so a run can never be longer and the walk always terminates.
    for (int steps = 0; steps < 7; steps++) {
      LocalDate previous = start.minusDays(1);
      if (!isWorkingDay(previous)) {
        return start;
      }
      start = previous;
    }
    return start;
  }

  /**
   * The previous working day strictly before {@code date}.
   *
   * <p>Section 5.2 step 5c resets the consecutive-late counter when yesterday was not late, so this
   * has to skip weekends and holidays rather than literally subtracting one day - otherwise a
   * Saturday late login followed by a Monday late login would never accumulate.
   *
   * @param date the reference day
   * @return the previous working day, or null when the calendar has none in reach
   */
  default LocalDate previousWorkingDay(LocalDate date) {
    LocalDate cursor = date.minusDays(1);
    for (int steps = 0; steps < 7; steps++) {
      if (isWorkingDay(cursor)) {
        return cursor;
      }
      cursor = cursor.minusDays(1);
    }
    return null;
  }

  /**
   * Whether an ISO day of week is in the company's working set.
   *
   * <p>Section 5.2 step 7's week-boundary logic only ever needs weekdays, so exposing this keeps
   * the day arithmetic readable without fabricating a date.
   *
   * @param dayOfWeek the ISO weekday
   * @return true when the company works that weekday
   */
  boolean isWorkingWeekday(DayOfWeek dayOfWeek);
}
