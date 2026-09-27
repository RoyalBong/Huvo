package com.huvo.payroll.payslip.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.payroll.payslip.entity.Payslip;

/** The payslip table. */
public interface PayslipRepository extends JpaRepository<Payslip, Long> {

  /** One employee's payslips, newest period first. */
  List<Payslip> findByEmployeeIdOrderByPeriodDesc(Long employeeId);

  /** Whether a payslip already exists for that employee and month. */
  Optional<Payslip> findByEmployeeIdAndPeriod(Long employeeId, LocalDate period);

  /** Everyone paid in a month, for the run's own reporting. */
  List<Payslip> findByPeriodOrderByEmployeeIdAsc(LocalDate period);
}
