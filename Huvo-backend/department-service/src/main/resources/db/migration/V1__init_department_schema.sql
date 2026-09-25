-- V1 - department-service baseline schema.
--
-- IF NOT EXISTS keeps this migration safe to run against a database that
-- Hibernate previously created with ddl-auto=update (see application.properties:
-- flyway.baseline-on-migrate + baseline-version=0).
CREATE TABLE IF NOT EXISTS department (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(255) NULL,
    location VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
