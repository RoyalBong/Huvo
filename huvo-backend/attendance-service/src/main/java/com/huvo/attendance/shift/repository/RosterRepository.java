package com.huvo.attendance.shift.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.huvo.attendance.shift.entity.Roster;

/** Shift definitions and the roster assignments that decide who is expected when. */
public interface RosterRepository extends JpaRepository<Roster, Long> {

  /**
   * The assignment in force for an employee on a given day.
   *
   * <p>The range test lives in the query rather than in Java so a large roster is filtered by the
   * index on {@code (employee_id, effective_from, effective_to)} instead of being loaded whole.
   * {@code effective_to IS NULL} covers the current assignment.
   *
   * @param employeeId whose assignment
   * @param day the day being evaluated
   * @return the active assignment, or empty when they had none
   */
  @Query(
      """
      select r from Roster r
      where r.employeeId = :employeeId
        and r.effectiveFrom <= :day
        and (r.effectiveTo is null or r.effectiveTo >= :day)
      order by r.effectiveFrom desc
      """)
  List<Roster> findActiveOn(@Param("employeeId") Long employeeId, @Param("day") LocalDate day);
}
