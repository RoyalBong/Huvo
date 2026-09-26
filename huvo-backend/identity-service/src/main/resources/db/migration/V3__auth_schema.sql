-- V3 - identity-service: auth domain tables (Huvo_Backend_Context.md Sections 3.3, 4).
--
-- Section 3.3 boundary rule: the auth sub-package owns these tables; employee and
-- department domains never read them directly, they see the caller via JWT claims.
-- Section 4.1: exactly one Access Role per user (ADMIN/HR/MANAGER/EMPLOYEE).
-- Schema only, never seed data (Section 2.1) - the first ADMIN is created by the
-- AuthBootstrap event below, not by this migration.
CREATE TABLE IF NOT EXISTS app_user (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    username          VARCHAR(190) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    employee_id       BIGINT       NULL,
    refresh_token_hash VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Department scope copied into the JWT departmentIds claim (Section 4.2).
CREATE TABLE IF NOT EXISTS app_user_department (
    user_id       BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_app_user_department_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
