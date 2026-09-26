-- V1 - attendance-service: the Section 5.1 data model, schema huvo_attendance.
--
-- Schema only, never seed data (Section 2.1). There is deliberately no INSERT here:
-- shifts, rosters and company working days are all company configuration that an
-- administrator supplies, and inventing placeholder rows would make a fresh install look
-- like it had attendance history it does not have.
--
-- Section 3.4: this is attendance-service's own schema. It never queries identity-service's
-- schema; the employee_id values here are kept in sync by the employee.created /
-- employee.updated events (Section 5.1).

-- A named block of hours with its own lateness allowance. grace_minutes defaults to 15 and
-- is per shift, not global, so a late-shift team can have a different rule.
CREATE TABLE IF NOT EXISTS shift (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    -- Local wall-clock times, deliberately not datetimes: "the 09:00 shift" is a clock
    -- concept, and storing a zone would imply a date it does not have.
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    grace_minutes INT          NOT NULL DEFAULT 15,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shift_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Which shift an employee works, over a date range. effective_to IS NULL means "current",
-- so a closed assignment is a historical fact rather than a deleted row.
CREATE TABLE IF NOT EXISTS roster (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT      NOT NULL,
    shift_id      BIGINT      NOT NULL,
    effective_from DATE       NOT NULL,
    effective_to  DATE        NULL,
    PRIMARY KEY (id),
    KEY ix_roster_employee (employee_id, effective_from, effective_to),
    CONSTRAINT fk_roster_shift FOREIGN KEY (shift_id) REFERENCES shift (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Which weekdays the company works (Section 5.2 step 7, Section 5.3 holiday handling).
-- Configurable, so week boundaries and "is today a working day" are never hardcoded to
-- Mon-Fri. ISO day-of-week: 1 = Monday .. 7 = Sunday.
CREATE TABLE IF NOT EXISTS company_working_days (
    company_id BIGINT NOT NULL,
    day_of_week INT    NOT NULL,
    PRIMARY KEY (company_id, day_of_week)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- The raw fact table (Section 5.1): immutable, append-only, one row per login attempt that
-- succeeded. It exists independently of the rules engine so the facts survive any bug or
-- rule change in the engine above it - the engine derives, this table records.
--
-- There is no updated_at and no status column here, and the application layer never
-- updates or deletes a row in this table. That is the whole contract.
CREATE TABLE IF NOT EXISTS login_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT       NOT NULL,
    login_timestamp DATETIME(6)  NOT NULL,
    source_ip       VARCHAR(45)  NULL,
    device          VARCHAR(100) NULL,
    -- The producing event's id, for at-least-once delivery (Section 7): a redelivered
    -- login event must not double-count, so the consumer dedupes on this.
    source_event_id VARCHAR(36)  NULL,
    PRIMARY KEY (id),
    KEY ix_login_event_employee_time (employee_id, login_timestamp),
    UNIQUE KEY uk_login_event_source_event (source_event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- One row per employee per day: the outcome. Unique on (employee_id, date) because two
-- logins on the same day must not create two competing outcomes.
CREATE TABLE IF NOT EXISTS attendance_day (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT      NOT NULL,
    date           DATE        NOT NULL,
    status         ENUM ('PRESENT', 'LATE', 'ABSENT', 'ON_LEAVE', 'HOLIDAY') NOT NULL,
    first_login_at DATETIME(6) NULL,
    is_auto_marked BOOLEAN     NOT NULL DEFAULT FALSE,
    reason_note    VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_day_employee_date (employee_id, date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Weekly lateness counter, one row per employee per configured week. week_start_date anchors the
-- week; the scheduled job in Section 5.2 step 7 starts a new row rather than editing the old one,
-- so last week's history stays intact.
--
-- NOTE there is deliberately NO consecutive_late_days_count column here. Section 5.1 lists one, but
-- storing it in a week-keyed row made it wrong: a new week reset it to 0, so the three-consecutive-
-- late-days rule in Section 5.2 step 6 could never fire - the two-late-days-in-a-week rule always
-- escalated first and reset the streak before it reached three. A streak is a property of an
-- employee's recent history, not of a week, so it is derived from attendance_day instead (the
-- consumer walks back over scheduled working days). Frequency and streak are two independent
-- patterns and neither is a fallback for the other.
CREATE TABLE IF NOT EXISTS late_tracker (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT NOT NULL,
    week_start_date DATE   NOT NULL,
    late_days_count INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_late_tracker_employee_week (employee_id, week_start_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;