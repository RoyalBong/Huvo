package com.huvo.payroll.payslip.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.audit.AuditClient;
import com.huvo.audit.AuditEntry;
import com.huvo.events.EventTypes;
import com.huvo.payroll.messaging.PayrollEventPublisher;
import com.huvo.payroll.payslip.entity.PayrollRun;
import com.huvo.payroll.payslip.entity.Payslip;
import com.huvo.payroll.payslip.entity.SalaryStructure;
import com.huvo.payroll.payslip.repository.PayrollRunRepository;
import com.huvo.payroll.payslip.repository.PayslipRepository;
import com.huvo.payroll.payslip.repository.SalaryStructureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generating a month's payslips.
 *
 * <p>Explicitly triggered, never scheduled. Section 12 Phase 4 calls payroll "a monthly batch
 * pattern", but nothing specifies the date, the cut-off, or who starts it, and a schedule that pays
 * people from an untested rule is not a decision to make silently.
 *
 * <p>Guaranteed-audited. Generating payroll is the most consequential thing in the system, so a run
 * that cannot be recorded does not proceed - the same reasoning as attendance's admin override.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollRunService {

  private static final String SERVICE = "payroll-service";

  private final PayrollRunRepository runs;
  private final SalaryStructureRepository structures;
  private final PayslipRepository payslips;
  private final PayrollEventPublisher publisher;
  private final ObjectProvider<AuditClient> auditClientProvider;

  @org.springframework.beans.factory.annotation.Value("${huvo.payroll.currency:AED}")
  private String currency;

  /**
   * Generates a payslip for every employee with a salary structure effective in that month.
   *
   * @param period the month; normalised to its first day so "September" has one representation
   * @param generatedBy the triggering user id, or null for a scheduled run
   * @return the completed run
   */
  @Transactional
  public PayrollRun generate(LocalDate period, String generatedBy) {
    LocalDate month = period.withDayOfMonth(1);
    LocalDate monthEnd = month.withDayOfMonth(period.lengthOfMonth());

    PayrollRun run = new PayrollRun();
    run.setPeriod(month);
    run.setStatus(PayrollRun.Status.RUNNING);
    run.setGeneratedBy(generatedBy);
    run.setStartedAt(OffsetDateTime.now().toLocalDateTime());
    run = runs.save(run);
    record(generatedBy, "payroll.run.started", run.getId(), month.toString());

    OffsetDateTime now = OffsetDateTime.now();
    int generated = 0;
    for (Long employeeId : structures.findDistinctEmployeeIdsEffectiveDuring(month, monthEnd)) {
      if (payslips.findByEmployeeIdAndPeriod(employeeId, month).isPresent()) {
        // Already paid this month. Skipping rather than overwriting is the point of the unique key:
        // a re-run has to be safe, and quietly replacing figures someone was already given would be
        // the worst version of that.
        log.info("Payslip already exists for employee {} in {}", employeeId, month);
        continue;
      }
      List<SalaryStructure> effective = structures.findEffectiveDuring(employeeId, month, monthEnd);
      var amounts = PayslipCalculator.calculate(effective, month);

      Payslip payslip = new Payslip();
      payslip.setEmployeeId(employeeId);
      payslip.setPeriod(month);
      payslip.setCurrency(currency);
      payslip.setGrossAmount(amounts.gross());
      payslip.setNetAmount(amounts.net());
      payslip.setRunId(run.getId());
      payslip.setGeneratedAt(now.toLocalDateTime());
      payslip.setCreatedAt(now.toLocalDateTime());
      payslips.save(payslip);
      generated++;
    }

    run.setPayslipCount(generated);
    run.setStatus(PayrollRun.Status.COMPLETED);
    run.setCompletedAt(now.toLocalDateTime());
    run = runs.save(run);

    record(generatedBy, "payroll.run.completed", run.getId(), generated + " payslip(s)");
    publisher.publishPayrollGenerated(
        new EventTypes.PayrollGeneratedPayload(run.getId(), month, generated, generatedBy));
    return run;
  }

  /** One employee's payslips, newest period first. */
  @Transactional(readOnly = true)
  public List<Payslip> forEmployee(Long employeeId) {
    return payslips.findByEmployeeIdOrderByPeriodDesc(employeeId);
  }

  /** Every run, most recent first. */
  @Transactional(readOnly = true)
  public List<PayrollRun> runs() {
    return runs.findAllByOrderByPeriodDesc();
  }

  /** Guaranteed audit: a missing client refuses the run. */
  private void record(String actorUserId, String action, Long runId, String detail) {
    AuditClient audit = auditClientProvider.getIfAvailable();
    if (audit == null) {
      throw new IllegalStateException(
          "No audit client is configured, so a payroll run cannot be recorded. Refusing: a run "
              + "that cannot be audited must not proceed. Set AUDIT_TABLE.");
    }
    audit.record(
        AuditEntry.of(
            actorUserId,
            null,
            action,
            "payroll_run",
            String.valueOf(runId),
            SERVICE,
            Map.of("detail", detail == null ? "" : detail)));
  }
}
