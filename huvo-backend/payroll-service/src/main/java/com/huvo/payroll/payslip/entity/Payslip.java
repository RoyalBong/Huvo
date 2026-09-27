package com.huvo.payroll.payslip.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One generated payslip.
 *
 * <p>The figures are stored, not recomputed on read. A payslip is a historical record: if the
 * salary structure is corrected later, a payslip already issued must not silently change underneath
 * someone. That is why the gross and net live here rather than being derived from the structure.
 */
@Entity
@Table(name = "payslip")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payslip {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "employee_id", nullable = false)
  private Long employeeId;

  /** The month, stored as the first day of that month. */
  @Column(nullable = false)
  private LocalDate period;

  @Column(nullable = false, length = 3)
  private String currency = "AED";

  @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal netAmount;

  /** Denormalised so a report does not have to join back to the run. */
  @Column(name = "run_id")
  private Long runId;

  /**
   * The payslip PDF in S3, under the {@code payslips/} prefix (Section 8.1).
   *
   * <p>Nullable on purpose: the row is the record, and a document rendered after the row exists may
   * fail without the payslip itself being invalid.
   */
  @Column(name = "object_key", length = 500)
  private String objectKey;

  @Column(name = "generated_at", nullable = false)
  private LocalDateTime generatedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
