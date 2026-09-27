-- V1 - worklife-service: task, leave and onboarding, schema huvo_worklife.
--
-- Schema only, never seed data (Section 2.1). No sample employees, tasks or leave - a fresh
-- install must look empty, not pre-populated.
--
-- Section 3.4: this is worklife-service's own schema. It never queries identity-service's schema;
-- employee_id values arrive by event and are kept here so the departments needed by Section 6.2's
-- assignment rule can be checked without a cross-service call on the hot path.

-- A unit of work. status follows Section 6.1's progression, with OVERDUE and LATE_SUBMITTED as
-- flags the deadline sweep and the submit path set.
--
-- deadline is NULLABLE on purpose: an open-ended task has no deadline, and defaulting one would
-- make every task inventably overdue. The overdue sweep and the late-submission check both treat
-- NULL as "never late".
CREATE TABLE IF NOT EXISTS task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    title             VARCHAR(200)  NOT NULL,
    description       TEXT          NULL,
    employee_id       BIGINT        NOT NULL,
    assigned_by_user  VARCHAR(64)   NULL,
    department_id     VARCHAR(64)   NULL,
    status            ENUM ('ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'COMPLETED', 'OVERDUE', 'LATE_SUBMITTED') NOT NULL DEFAULT 'ASSIGNED',
    deadline          DATETIME(6)   NULL,
    submitted_at      DATETIME(6)   NULL,
    completed_at      DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- The deadline sweep (Section 6.2) scans open tasks by deadline every 15 minutes, so the index
    -- leads with the status it filters on.
    KEY ix_task_open_deadline (status, deadline),
    KEY ix_task_employee (employee_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- A file handed in against a task. The client uploads straight to S3 with a pre-signed URL
-- (Section 6.2) and this table holds only the resulting object key and metadata - never the bytes.
CREATE TABLE IF NOT EXISTS task_submission (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    task_id       BIGINT       NOT NULL,
    employee_id   BIGINT       NOT NULL,
    object_key    VARCHAR(500)  NOT NULL,
    original_name VARCHAR(255)  NULL,
    content_type  VARCHAR(120)  NULL,
    size_bytes    BIGINT        NULL,
    submitted_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_submission_task (task_id),
    CONSTRAINT fk_submission_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- A leave request and its decision. from_date/to_date are inclusive, matching the
-- leave.approved contract attendance-service already consumes - the producer must not reshape it.
CREATE TABLE IF NOT EXISTS leave_request (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT      NOT NULL,
    leave_type     VARCHAR(40) NOT NULL,
    from_date      DATE        NOT NULL,
    to_date        DATE        NOT NULL,
    reason         VARCHAR(500) NULL,
    status         ENUM ('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    decided_by     VARCHAR(64) NULL,
    decision_note  VARCHAR(500) NULL,
    decided_at     DATETIME(6) NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_leave_employee_status (employee_id, status),
    -- An approver's queue: pending requests, oldest first.
    KEY ix_leave_pending (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Onboarding checklist state per new hire.
--
-- NOTE the column names are provisional. Section 3.3 and Section 12 name an onboarding domain but
-- the context document specifies no states, no steps and no rules for it - only that it belongs here.
-- This table is a minimal, honest placeholder that records which steps are done; the shape should be
-- confirmed against the product's actual onboarding requirements before it is relied on.
CREATE TABLE IF NOT EXISTS onboarding_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT       NOT NULL,
    step_key     VARCHAR(60)  NOT NULL,
    label        VARCHAR(200) NOT NULL,
    completed    BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- One row per step per employee, so a checklist cannot be created twice.
    UNIQUE KEY uk_onboarding_employee_step (employee_id, step_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;