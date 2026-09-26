-- V2 - identity-service: department domain baseline schema (folded in from
-- department-service, Section 12 Phase 1). Same huvo_identity schema as V1.
--
-- IF NOT EXISTS keeps this migration safe to run against a database that
-- Hibernate previously created with ddl-auto=update (see application.properties:
-- flyway.baseline-on-migrate + baseline-version=0).
CREATE TABLE IF NOT EXISTS department (