package com.huvo.attendance.tracker.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.attendance.tracker.entity.LateTracker;

/** The week-scoped late-day counters (Section 5.1). */
public interface LateTrackerRepository extends JpaRepository<LateTracker, Long> {

  /**
   * The counter row for an employee in a given week.
   *
   * <p>Keyed on the derived week anchor rather than a hardcoded Monday, so a Mon-Sat company gets a
   * different set of rows than a Mon-Fri one.
   *
   * @param employeeId whose counter
   * @param weekStartDate the week's anchor
   * @return the row, or empty when they have not been late this week yet
   */
  Optional<LateTracker> findByEmployeeIdAndWeekStartDate(Long employeeId, LocalDate weekStartDate);
}
