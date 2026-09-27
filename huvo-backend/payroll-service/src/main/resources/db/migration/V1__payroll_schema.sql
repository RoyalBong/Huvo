-- V1 - payroll-service: salary structure and payslips, schema huvo_payroll.
--
-- Schema only, never seed data (Section 2.1).
--
-- Scope note, deliberate. Section 12 Phase 4 asks for "salary structure, payslip generation", and
-- Section 3.2 marks payroll as the lowest-priority service, with the product vision (Section 0)
-- saying "do not over-engineer payroll". So this is the smallest structure that can produce a
-- payslip, and nothing more:
--
--   * no deductions, no benefits, no tax, no provident-fund, no overtime rates, no leave
--     encashment, no arrears. Every one of those is a real payroll concern and none of them is
--     specified anywhere in the context document. They are not stubs to be filled in later - the
--     schema below has no column for them, so a payslip cannot quietly imply one was considered
--     and got a zero.
--   * no prorating. A month is a month; the gross is the monthly base.
--
-- The only event payroll consumes is attendance.autoAbsent.triggered, which Section 7 marks
-- "(future)". It is recorded for visibility and changes no figure here, because the rule linking an
-- absence to a deduction is not specified and inventing one would be worse than not having it.

-- An employee's pay for one effective period.
--
-- Amounts are DECIMAL(12,2) in major currency units, never floating point: money that cannot
-- represent 0.01 exactly is money that does not add up, and a payslip that does not reconcile is
-- worse than no payroll.
--
-- effective_from/effective_to make this a history rather than a mutable current value, because a
-- raise mid-year has to pay the old rate for the old month. NULL effective_to means current.
CREATE TABLE IF NOT EXISTS salary_structure (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT         NOT NULL,
    -- Free text for now (BASIC, ALLOWANCE, ...). No component taxonomy is specified.
    component       VARCHAR(40)    NOT NULL DEFAULT 'BASIC',
    amount          DECIMAL(12, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'AED',
    effective_from  DATE           NOT NULL,
    effective_to    DATE           NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- "What is this employee paid as of date X" is the only read this table ever needs.
    KEY ix_salary_employee_effective (employee_id, effective_from, effective_to)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- One generated payslip.
--
-- The figures are stored, not recomputed on read. A payslip is a historical record: if the salary
-- structure is corrected later, a payslip already issued must not silently change underneath
-- someone. This is the reason the table holds its own gross/net rather than referencing the
-- structure.
--
-- object_key points at the PDF in S3 under the payslips/ prefix (Section 8.1). Nullable, because a
-- document is generated after the row exists and the row is the record even if rendering fails.
CREATE TABLE IF NOT EXISTS payslip (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT         NOT NULL,
    period          DATE           NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'AED',
    gross_amount    DECIMAL(12, 2) NOT NULL,
    net_amount      DECIMAL(12, 2) NOT NULL,
    -- Denormalised so a report does not have to join back to the run.
    run_id          BIGINT         NULL,
    object_key      VARCHAR(500)    NULL,
    generated_at    DATETIME(6)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- One payslip per employee per month. Enforced by the database rather than only by service
    -- logic, so a re-run of the month cannot pay someone twice.
    UNIQUE KEY uk_payslip_employee_period (employee_id, period),
    KEY ix_payslip_run (run_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- A payroll run, so a month is a single auditable act rather than N unrelated inserts.
CREATE TABLE IF NOT EXISTS payroll_run (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    period        DATE        NOT NULL,
    status        ENUM ('RUNNING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'RUNNING',
    payslip_count INT         NOT NULL DEFAULT 0,
    generated_by  VARCHAR(64) NULL,
    started_at    DATETIME(6) NOT NULL,
    completed_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- One run per month; a re-run is an update of the existing run, never a second one.
    UNIQUE KEY uk_payroll_run_period (period)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
