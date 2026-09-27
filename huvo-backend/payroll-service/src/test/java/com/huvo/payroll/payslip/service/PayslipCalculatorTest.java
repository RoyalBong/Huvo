package com.huvo.payroll.payslip.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.huvo.payroll.payslip.entity.SalaryStructure;

/**
 * The pay arithmetic.
 *
 * <p>Weighted towards the two things most likely to be wrong in payroll and least likely to be
 * noticed: money precision, and which structure applies to which month.
 */
class PayslipCalculatorTest {

  private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 15);

  private static SalaryStructure structure(
      String amount, LocalDate from, LocalDate to, String component) {
    SalaryStructure s = new SalaryStructure();
    s.setId(1L);
    s.setEmployeeId(42L);
    s.setComponent(component);
    s.setAmount(new BigDecimal(amount));
    s.setCurrency("AED");
    s.setEffectiveFrom(from);
    s.setEffectiveTo(to);
    s.setCreatedAt(LocalDateTime.now());
    return s;
  }

  @Test
  void aSingleStructureIsTheGross() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(structure("12000.00", LocalDate.of(2020, 1, 1), null, "BASIC")), SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("12000.00");
    assertThat(amounts.net()).isEqualByComparingTo("12000.00");
  }

  @Test
  void severalComponentsSumIntoOnePayslip() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(
                structure("12000.00", LocalDate.of(2020, 1, 1), null, "BASIC"),
                structure("1500.50", LocalDate.of(2020, 1, 1), null, "ALLOWANCE")),
            SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("13500.50");
  }

  @Test
  void anEmployeeWithNoStructureIsPaidNothingRatherThanOmitted() {
    // A zero payslip is a fact; no payslip at all is indistinguishable from not being on payroll.
    var amounts = PayslipCalculator.calculate(List.of(), SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("0.00");
    assertThat(amounts.net()).isEqualByComparingTo("0.00");
  }

  @Test
  void amountsAreHeldToTheCurrencyScale() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(structure("100.005", LocalDate.of(2020, 1, 1), null, "BASIC")), SEPTEMBER);

    // Two decimal places, half-up. A payslip that carries a third decimal is a reconciliation
    // argument waiting to happen.
    assertThat(amounts.gross().scale()).isEqualTo(2);
    assertThat(amounts.gross()).isEqualByComparingTo("100.01");
  }

  @Test
  void amountsAreExactWhereBinaryFloatingPointWouldNotBe() {
    // 0.1 + 0.2 is famously not 0.3 in a double. The whole reason this is BigDecimal.
    var amounts =
        PayslipCalculator.calculate(
            List.of(
                structure("0.10", LocalDate.of(2020, 1, 1), null, "A"),
                structure("0.20", LocalDate.of(2020, 1, 1), null, "B")),
            SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("0.30");
    assertThat(amounts.gross().toPlainString()).isEqualTo("0.30");
  }

  @Test
  void aStructureStartingMidMonthStillPaysThatMonth() {
    // A new joiner on the 15th is not paid nothing for their first month; treating a mid-month
    // start
    // as not-yet-effective is the classic way a payroll run quietly misses someone.
    var amounts =
        PayslipCalculator.calculate(
            List.of(structure("8000.00", LocalDate.of(2026, 9, 15), null, "BASIC")), SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("8000.00");
  }

  @Test
  void aStructureStartingNextMonthDoesNotPayThisMonth() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(structure("8000.00", LocalDate.of(2026, 10, 1), null, "BASIC")), SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("0.00");
  }

  @Test
  void aStructureEndingThisMonthStillPaysIt() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(
                structure("8000.00", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 30), "BASIC")),
            SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("8000.00");
  }

  @Test
  void aStructureEndingLastMonthDoesNotPayThisMonth() {
    var amounts =
        PayslipCalculator.calculate(
            List.of(
                structure("8000.00", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 8, 31), "BASIC")),
            SEPTEMBER);

    assertThat(amounts.gross()).isEqualByComparingTo("0.00");
  }

  @Test
  void netEqualsGrossBecauseNothingIsDeducted() {
    // Stated as a test so that adding a deduction later is a deliberate change rather than an
    // assumption someone makes when they first see the two fields are equal.
    var amounts =
        PayslipCalculator.calculate(
            List.of(structure("12000.00", LocalDate.of(2020, 1, 1), null, "BASIC")), SEPTEMBER);

    assertThat(amounts.net()).isEqualTo(amounts.gross());
  }

  @Test
  void aMidMonthRaisePaysTheNewRateForTheWholeMonth() {
    // Wrong, and deliberately so. Prorating needs a rule the context document does not contain, and
    // the alternative to recording that is a payslip nobody can reconstruct. Pinned so that whoever
    // adds proration has to come here and change this test on purpose.
    var amounts =
        PayslipCalculator.calculate(
            List.of(
                structure("10000.00", LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 14), "BASIC"),
                structure("15000.00", LocalDate.of(2026, 9, 15), null, "BASIC")),
            SEPTEMBER);

    // Both structures overlap September, so both are summed - 25000, which is not a real answer for
    // any month and is exactly why this needs changing before it is ever used in anger.
    assertThat(amounts.gross()).isEqualByComparingTo("25000.00");
  }
}
