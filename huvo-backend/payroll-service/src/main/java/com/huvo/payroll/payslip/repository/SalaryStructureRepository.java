package com.huvo.payroll.payslip.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.huvo.payroll.payslip.entity.SalaryStructure;

/** The salary_structure table. */
public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

  /** An employee's whole pay history, oldest first. */
  List<SalaryStructure> findByEmployeeIdOrderByEffectiveFromAsc(Long employeeId);

  /**
   * The structures that could apply to a month.
   *
   * <p>Range overlap rather than a point-in-time lookup, because a structure starting mid-month
   * still pays something that month. The final inclusivity decision is left to {@code
   * SalaryStructure.isEffectiveDuring} so it lives in one readable place rather than twice - once
   * as a query and once in Java, where the two can quietly disagree.
   *
   * @param employeeId whose pay
   * @param monthStart the first day of the month
   * @param monthEnd the last day of the month
   * @return the structures that could apply, oldest first
   */
  @Query(
      """
      select s from SalaryStructure s
      where s.employeeId = :employeeId
        and s.effectiveFrom <= :monthEnd
        and (s.effectiveTo is null or s.effectiveTo >= :monthStart)
      order by s.effectiveFrom asc
      """)
  List<SalaryStructure> findEffectiveDuring(
      @Param("employeeId") Long employeeId,
      @Param("monthStart") LocalDate monthStart,
      @Param("monthEnd") LocalDate monthEnd);

  /**
   * Distinct employees with any structure overlapping a month - i.e. who a run should pay.
   *
   * <p>Distinct rather than one row per structure: an employee with a BASIC and an ALLOWANCE has
   * two rows and must still get one payslip, so the run's employee list has to be deduplicated
   * before it iterates or the unique (employee, period) key turns the second structure into a
   * constraint violation.
   *
   * @param monthStart the first day of the month
   * @param monthEnd the last day of the month
   * @return the employee ids to pay, ascending
   */
  @Query(
      """
      select distinct s.employeeId from SalaryStructure s
      where s.effectiveFrom <= :monthEnd
        and (s.effectiveTo is null or s.effectiveTo >= :monthStart)
      order by s.employeeId asc
      """)
  List<Long> findDistinctEmployeeIdsEffectiveDuring(
      @Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);
}
