package com.huvo.payroll.payslip.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.payroll.payslip.entity.PayrollRun;

/** The payroll_run table. */
public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

  /** The run for a month, if there is one. */
  Optional<PayrollRun> findByPeriod(LocalDate period);

  /** Every run, most recent month first. */
  List<PayrollRun> findAllByOrderByPeriodDesc();
}
