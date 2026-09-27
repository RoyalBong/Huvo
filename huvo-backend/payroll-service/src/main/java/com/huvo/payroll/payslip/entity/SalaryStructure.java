package com.huvo.payroll.payslip.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
 * An employee's pay for one effective period.
 *
 * <p>Append-only with {@code effectiveFrom}/{@code effectiveTo} rather than a mutable current
 * value, because a raise has to pay the old rate for the old month. Overwriting in place would make
 * a re-run of a closed month pay the new rate, and nobody would notice until someone compared
 * payslips.
 */
@Entity
@Table(name = "salary_structure")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryStructure {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "employee_id", nullable = false)
  private Long employeeId;

  /** Free text for now (BASIC, ALLOWANCE, ...). No component taxonomy is specified. */
  @Column(nullable = false, length = 40)
  private String component = "BASIC";

  /**
   * DECIMAL(12,2) in the database and {@link BigDecimal} here - never {@code double}. Money that
   * cannot represent 0.01 exactly is money that does not add up, and a payslip that does not
   * reconcile is worse than no payroll at all.
   */
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "AED";

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  /** Null means current. */
  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /**
   * Whether this structure was in effect at any point during a month.
   *
   * <p>Any day in the month, not the first. A structure starting on the 15th still paid something
   * that month, and treating it as not-yet-effective would pay a new joiner nothing for their first
   * month.
   *
   * @param period the month
   * @return true when it applies
   */
  @JsonIgnore
  public boolean isEffectiveDuring(LocalDate period) {
    LocalDate monthStart = period.withDayOfMonth(1);
    LocalDate monthEnd = period.withDayOfMonth(period.lengthOfMonth());
    if (effectiveFrom.isAfter(monthEnd)) {
      return false;
    }
    return effectiveTo == null || !effectiveTo.isBefore(monthStart);
  }
}
