package com.huvo.attendance.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.huvo.attendance.calendar.entity.CompanyWorkingDay;
import com.huvo.attendance.calendar.repository.CompanyWorkingDayRepository;
import com.huvo.attendance.engine.FixedWorkingCalendar;
import com.huvo.attendance.engine.WorkingCalendar;

/**
 * The production {@link WorkingCalendar}, backed by {@code company_working_days} (Section 5.1).
 *
 * <p>The configured set is read once at startup. A working calendar is company configuration that
 * changes on an order of months, not minutes, so re-querying it on every login - the one operation
 * in this service that genuinely spikes - would be a pointless database round trip on the hottest
 * path in the product. An admin changing the calendar restarts the service.
 *
 * <p>Mon-Fri is the fallback when the table is empty, so a fresh install behaves sensibly instead
 * of treating every day as a holiday. It is a documented default rather than seed data, so it does
 * not violate the no-mock-data rule (Section 2.1).
 */
@Component
public class DatabaseWorkingCalendar implements WorkingCalendar {

  private final Set<DayOfWeek> workingDays;

  /**
   * @param repository the configured weekdays
   * @param companyId which company's calendar to read
   */
  public DatabaseWorkingCalendar(
      CompanyWorkingDayRepository repository,
      @Value("${huvo.attendance.company-id:1}") Long companyId) {
    List<CompanyWorkingDay> configured = repository.findByCompanyId(companyId);
    if (configured.isEmpty()) {
      this.workingDays = FixedWorkingCalendar.mondayToFriday().workingDays();
      return;
    }
    EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    for (CompanyWorkingDay day : configured) {
      days.add(DayOfWeek.of(day.getDayOfWeek()));
    }
    if (days.isEmpty()) {
      throw new IllegalStateException(
          "Company " + companyId + " has working days configured but none could be resolved");
    }
    this.workingDays = Set.copyOf(days);
  }

  @Override
  public boolean isWorkingDay(LocalDate date) {
    return workingDays.contains(date.getDayOfWeek());
  }

  @Override
  public boolean isWorkingWeekday(DayOfWeek dayOfWeek) {
    return workingDays.contains(dayOfWeek);
  }

  /** The configured weekdays, for the admin read API. */
  public Set<DayOfWeek> workingDays() {
    return workingDays;
  }
}
