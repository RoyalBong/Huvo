package com.huvo.payroll.payslip.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A payroll run, so a month is one auditable act rather than N unrelated inserts.
 *
 * <p>Worth having even in a service this small: a run that half-failed needs to be identifiable as
 * a run, and "the payslips for September, some of which exist" is a much harder thing to reason
 * about after an incident than a row saying the run failed.
 */
@Entity
@Table(name = "payroll_run")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRun {

  /** Where a run has got to. Terminal states are COMPLETED and FAILED. */
  public enum Status {
    RUNNING,
    COMPLETED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The month, stored as the first day. Unique, so a re-run updates rather than duplicates. */
  @Column(nullable = false)
  private LocalDate period;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.RUNNING;

  @Column(name = "payslip_count", nullable = false)
  private int payslipCount;

  /** Null for a scheduled run - "who" is not always a person. */
  @Column(name = "generated_by", length = 64)
  private String generatedBy;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;
}
