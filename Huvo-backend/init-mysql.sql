-- Local/dev MySQL bootstrap (Huvo_Backend_Context.md Sections 3.4 and 8.1):
-- one MySQL instance, one schema per service. Schema only - no seed data (§2.1).
-- On AWS these schemas live on the RDS instance and are created by each service's
-- Flyway migrations (the CloudFormation stack itself is Phase 6 in Section 12).
CREATE DATABASE IF NOT EXISTS huvo_identity;
CREATE DATABASE IF NOT EXISTS huvo_attendance;
CREATE DATABASE IF NOT EXISTS huvo_worklife;
CREATE DATABASE IF NOT EXISTS huvo_payroll;