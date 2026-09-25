-- V1 - employee-service baseline schema.
--
-- IF NOT EXISTS keeps this migration safe to run against a database that
-- Hibernate previously created with ddl-auto=update (see application.properties:
-- flyway.baseline-on-migrate + baseline-version=0).
CREATE TABLE IF NOT EXISTS employee (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255) NULL,
    department_id VARCHAR(255) NULL,
    salary        DOUBLE       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
