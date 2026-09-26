package com.huvo.attendance.day.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.attendance.day.entity.AttendanceDay;

/**
 * The per-day outcomes (Section 5.1).
 *
 * <p>The history queries exist for the streak derivation, which walks back over scheduled working
 * days reading this table - the source of truth for whether a day was late.
 */
public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {

  Optional<AttendanceDay> findByEmployeeIdAndDate(Long employeeId, LocalDate date);

  boolean existsByEmployeeIdAndDate(Long employeeId, LocalDate date);

  /**
   * Every outcome for one employee within a date range, oldest first.
   *
   * <p>Used by the streak derivation, which needs the recent history in order to walk backwards.
   * The range is bounded by the caller (a streak cannot exceed a working week), so this never
   * returns the employee's whole history.
   *
   * @param employeeId whose history
   * @param from inclusive lower bound
   * @param to exclusive upper bound
   * @return the days in range, ascending by date
   */
  List<AttendanceDay> findByEmployeeIdAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
      Long employeeId, LocalDate from, LocalDate to);

  /** Days in a month for the dashboard read, newest first. */
  List<AttendanceDay> findByEmployeeIdAndDateBetweenOrderByDateDesc(
      Long employeeId, LocalDate from, LocalDate to);
}
