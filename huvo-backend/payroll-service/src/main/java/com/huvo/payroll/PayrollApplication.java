package com.huvo.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payroll-service (Huo_Backend_Context.md Sections 3.1, 3.2, 12).
 *
 * <p>Owns salary structure and payslip generation. The last of the five services, and deliberately
 * the smallest: Section 3.2 marks it lowest-priority and the product vision (Section 0) says "do
 * not over-engineer payroll", so the schema carries no deduction, tax, benefit or proration column
 * - none of those is specified anywhere in the context document.
 *
 * <p>No scheduler. A payroll run is triggered explicitly rather than on a cron: Section 12 Phase 4
 * describes a monthly batch, but nothing specifies the date, the cut-off, or who starts it, and
 * guessing a date that then pays people from an untested schedule is not a decision to make
 * silently.
 */
@SpringBootApplication
public class PayrollApplication {

  public static void main(String[] args) {
    SpringApplication.run(PayrollApplication.class, args);
  }
}
