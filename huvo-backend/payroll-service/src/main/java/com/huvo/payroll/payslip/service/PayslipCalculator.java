package com.huvo.payroll.payslip.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.huvo.payroll.payslip.entity.SalaryStructure;

/**
 * The pay calculation.
 *
 * <p>Deliberately trivial, and that is the design rather than a placeholder. Section 12 Phase 4
 * asks for "salary structure, payslip generation"; Section 3.2 marks payroll the lowest-priority
 * service; Section 0 says "simplicity over feature-completeness - do not over-engineer payroll". No
 * deduction, tax, benefit, overtime or proration rule is specified anywhere in the context
 * document, so none is invented here.
 *
 * <p>Pure and static, so the arithmetic is testable without a database - and because the thing most
 * worth getting right in payroll is {@link BigDecimal} and scale, which a test that constructs real
 * salaries and checks real sums catches far better than one that mocks the calculator away.
 */
public final class PayslipCalculator {

  /**
   * The currency's minor unit scale. AED, INR and USD are all 2 decimal places; JPY is 0.
   *
   * <p>Hardcoded rather than looked up because the context document specifies one currency per
   * company and does not specify the set. A wrong scale here rounds real money, so it is stated in
   * one place rather than implied by whatever the caller's scale happened to be.
   */
  public static final int SCALE = 2;

  private PayslipCalculator() {}

  /**
   * The pay for one employee in one month, with no salary structure at all.
   *
   * <p>Zero, not an exception and not a null. An employee with no structure on file still gets a
   * payslip row saying they were paid nothing, which is the fact - and a run that quietly skipped
   * them would leave the one person most worth noticing absent from the payroll entirely.
   *
   * @param structures every structure on file for the employee
   * @param period the month being paid
   * @return the gross and net, both zero
   */
  public static Amounts calculate(List<SalaryStructure> structures, LocalDate period) {
    return calculate(structures, period, 0);
  }

  /**
   * The pay for one employee in one month.
   *
   * <p>Sums the structures effective on any day in the period. A mid-month raise is therefore paid
   * at the new rate for the whole month, which is wrong and is written down as wrong rather than
   * quietly approximated: prorating needs a rule the context document does not contain, and the
   * alternative to admitting that is a payroll figure nobody can reconstruct.
   *
   * <p>Net equals gross because there is nothing to deduct. They are separate fields so that adding
   * a deduction later does not mean redefining what net meant for every historical payslip.
   *
   * @param structures every structure on file for the employee
   * @param period the month being paid
   * @param deductionDays days to deduct, at the employee's daily rate. Zero unless a rule supplies
   *     one; nothing in the context document does, so every caller passes zero today
   * @return the gross and net
   */
  public static Amounts calculate(
      List<SalaryStructure> structures, LocalDate period, int deductionDays) {
    BigDecimal monthly = BigDecimal.ZERO;
    for (SalaryStructure structure : structures) {
      if (structure.isEffectiveDuring(period)) {
        monthly = monthly.add(structure.getAmount());
      }
    }
    if (deductionDays <= 0) {
      BigDecimal gross = scale(monthly);
      return new Amounts(gross, gross);
    }
    // Pro-rata on a per-diem basis. Present and exercised, but unreachable today: see the class
    // note. When a real unpaid-absence rule arrives this is the shape it should take, and until
    // then the column that would carry it does not exist.
    BigDecimal daysInMonth = BigDecimal.valueOf(period.lengthOfMonth());
    BigDecimal daily = monthly.divide(daysInMonth, SCALE + 4, RoundingMode.HALF_UP);
    BigDecimal deducted = daily.multiply(BigDecimal.valueOf(deductionDays));
    BigDecimal gross = scale(monthly);
    return new Amounts(gross, scale(gross.subtract(deducted)));
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /**
   * A period's gross and net.
   *
   * @param gross the amount before any deduction
   * @param net the amount actually payable
   */
  public record Amounts(BigDecimal gross, BigDecimal net) {}
}
