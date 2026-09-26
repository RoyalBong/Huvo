# Huvo Backend Context

> Single source of truth for Cline (or any engineer) working on `huvo-backend`. Read fully before generating or modifying code. Where this conflicts with an earlier assumption in this file's history, this version wins — it reflects the latest, cost-optimized decision.

---

## 0. Naming & Repo Convention

Product name: **Huvo**. Before public launch, run a trademark/domain/app-store-name availability check — doesn't block engineering.

- Maven `groupId` root: `com.huvo.<servicename>`
- Deployable JAR / systemd unit names: `huvo-<servicename>` (e.g. `huvo-attendance-service.jar`, systemd unit `huvo-attendance-service.service`)
- Env var prefix: `HUVO_*`
- MySQL schema per service: `huvo_<servicename>` — see Section 3.2
- DynamoDB table prefix: `huvo_<tablename>`
- S3 bucket: `huvo-<env>-assets`
- CloudFormation stack/resource prefix (for later): `huvo-<env>-<resource>`

**Two separate repositories:**
- `huvo-backend` — all microservices, as described in this document
- `huvo-frontend` — the SPA, deployed to its own EC2

This document covers `huvo-backend` only.

---

## 1. Product Vision (Condensed for Engineering)

A modular HRMS for 10–200 employee companies covering: **Org Structure → Onboarding → Attendance → Tasks → Leave → Payroll → Offboarding**, plus **internal chat**. Priorities that should influence every engineering trade-off:

1. **Attendance automation correctness** (login-triggered, late-tracking, auto-absent rules) — the core differentiator, must be bulletproof, auditable, and **isolated from other load** (see Section 3).
2. **Task visibility** — real-time manager dashboards, no stale state.
3. **Simplicity over feature-completeness** — do not over-engineer payroll/chat.
4. **Deploy-ready, cost-optimized AWS deployment** — infrastructure as code (CloudFormation, generated in a later step), no Docker anywhere, no hardcoded environment values, and a deliberately lean process footprint.
5. **No mock/demo data in the product itself.** See Section 2.1 — a hard rule, not a style preference.

---

## 2. Existing Foundation & Key Decisions

- Employee Service and Department Service already exist (Spring Boot + MySQL) — extend, don't rebuild; both are being folded into `identity-service` (Section 3).
- RabbitMQ already integrated — kept, self-hosted, single node.
- Jenkins pipeline exists — adapted for JAR-based deploys (Section 9), not Docker image builds.

**Decisions locked in for this phase:**
- **No Docker, anywhere** — not in production, not in local development. Services run as plain executable JARs.
- **No Eureka, no Config Server, no Spring Cloud Gateway.** Nginx does path-based routing; configuration is via environment variables (SSM Parameter Store in prod, local property files in dev).
- **Backend is split into 5 microservices, grouped by load shape, not just by domain name.** The goal isn't "as many services as possible" or "as few as possible" — it's isolating the one service that has genuinely spiky, differentiator-critical load (attendance) while merging everything else that's safe to share a process, to keep infrastructure cost lean. See Section 3 for the full reasoning.
- **CloudFormation IaC is a separate, later deliverable** — this document describes the architecture the CloudFormation template will provision; the template itself comes in a follow-up request.

### 2.1 No Mock / Demo Data — Hard Rule

The backend must **never** ship with seeded fake employees, fake attendance records, fake tasks, or any "trial mode" sample dataset baked into migrations, startup code, or default profiles. Concretely:
- Flyway migrations create schema only — **no `INSERT` seed data** beyond strictly required reference/lookup data (e.g. a default `ADMIN` role row needed for the very first login to exist), and even that must be minimal and clearly commented as bootstrap-only, not "sample data."
- No `CommandLineRunner`/`@PostConstruct` classes that populate fake employees, fake attendance, or fake tasks "for testing" in any profile that could reach production.
- No hardcoded demo login credentials embedded in code.
- If a sandbox/demo environment is wanted later (e.g. for sales demos), it must be a **separate, explicitly-provisioned environment** (its own database, its own env profile, clearly named `demo`), never something that ships by default or shares infrastructure with real customer data.
- Test fixtures for unit/integration tests are fine and expected — the rule is about what ships in the running application, not about test code.

---

## 3. Target Architecture — 5 Services, Grouped by Load Shape

```
                     Route53 (domain, optional at MVP)
                          │
                 ┌────────┴─────────┐
                 │   (optional) ALB  │  ← add later for TLS + zero-downtime deploys
                 └────────┬─────────┘
        ┌──────────────────┴───────────────────┐
        ▼                                       ▼
 Frontend EC2 (t3.micro)               Backend EC2 (t4g.medium — ARM/Graviton)
 Nginx + built SPA static files        Nginx (reverse proxy, path-based routing)
        │                              + 5 systemd-managed microservice JARs
        │  calls API over HTTPS        + RabbitMQ (single node, systemd)
        └───────────────►◄───────────────────────┘
                          │
              ┌───────────┼─────────────┐
              ▼           ▼             ▼
      RDS MySQL       DynamoDB       S3 Bucket
   (1 instance,     (notifications,  (documents, task
    4 schemas,       chat, audit)     submissions, payslips,
    one per service)                  chat attachments)
```

### 3.1 Why This Grouping (Not Naive Consolidation)

Merging all 10 earlier fine-grained services back into 1–2 would undo the reason they were split in the first place — one hot domain would starve the others. The grouping below merges only where load profiles are compatible, and keeps isolation exactly where it earns its cost:

| Service | Contains | Why grouped/isolated this way |
|---|---|---|
| **identity-service** | auth + employee + department | Reference/lookup data, read-heavy, low write volume, already tightly coupled (every login needs role + department). Merging removes what would otherwise be the chattiest inter-service HTTP calls — they become in-process method calls instead. |
| **attendance-service** | *(standalone, unchanged)* | **Kept isolated on purpose.** This is the one domain with genuinely spiky load — everyone logging in around shift start — and it's the product's core differentiator. This is exactly the case where paying for a dedicated process is worth it; everything else is free to share. |
| **worklife-service** | task + leave + onboarding | Manager/employee-initiated workflows, steady/moderate volume, no peak-time collision with attendance, and they already share scheduler infra (deadline checks, document-expiry checks). |
| **payroll-service** | *(standalone, unchanged)* | Sensitive/compliance data with a monthly batch pattern (payslip generation) — shouldn't compete with real-time attendance traffic, and its data blast radius should stay separate from everything else. |
| **notify-service** | notification + chat | Event/message-driven, DynamoDB-backed, no synchronous coupling to anything — safe to share a process. |

That's **5 JVMs + RabbitMQ = 6 processes** total on the backend EC2, down from an earlier 11-process plan, while the one service that actually needed isolation still has it.

### 3.2 Deployable Units & Ports

| # | Service | Port | Data Store |
|---|---|---|---|
| 1 | **identity-service** | 8081 | `huvo_identity` (MySQL) |
| 2 | **attendance-service** | 8082 | `huvo_attendance` (MySQL) |
| 3 | **worklife-service** | 8083 | `huvo_worklife` (MySQL) + S3 (task submissions, documents) |
| 4 | **payroll-service** | 8084 | `huvo_payroll` (MySQL) + S3 (payslips) |
| 5 | **notify-service** | 8085 | DynamoDB (`huvo_notifications`, `huvo_chat_messages`) + S3 (chat attachments) |

Audit is **not** a separate service — every service writes directly to a shared DynamoDB table `huvo_audit_log` via a small shared library `huvo-audit-client` (in-process call, not a network hop). Reporting is **not** a separate service either — each service exposes its own read/aggregation endpoints against its own schema; a dedicated reporting service is a good later addition once real report requirements are known.

### 3.3 Internal Structure Within a Consolidated Service

Even though `identity-service` and `worklife-service` each cover multiple original domains, keep them as **separate internal packages with a clean boundary**, so a future split back into standalone services (if one of them genuinely outgrows the shared process) is a low-drama extraction, not a rewrite:

```
com.huvo.identity.auth.{controller,service,repository,dto,entity,event}
com.huvo.identity.employee.*
com.huvo.identity.department.*

com.huvo.worklife.attendance.*   ← n/a, attendance is its own service, shown for contrast
com.huvo.worklife.task.*
com.huvo.worklife.leave.*
com.huvo.worklife.onboarding.*
```
Rule: **domains never share entities or repositories across sub-packages, even within the same JAR.** Cross-domain calls go through the other domain's service-layer interface only (a Java method call, not a network call, since they're co-located) — this is what makes the boundary real rather than cosmetic, and what makes a future extraction safe.

### 3.4 Database Strategy

One RDS MySQL instance, **one schema per service** (`huvo_identity`, `huvo_attendance`, `huvo_worklife`, `huvo_payroll` — 4 schemas). No service ever queries another service's schema directly; cross-service data access happens via that service's REST API or via consumed events.

**Do you need MySQL at all? Yes.** Attendance, leave, payroll, and task-assignment data need relational integrity (foreign keys, transactions) and ad-hoc join-based reporting (e.g. "attendance % by department last month") that DynamoDB is a poor fit for. DynamoDB is used only where the access pattern is genuinely simple and write-heavy: chat messages, notifications, and the audit log — none of which need joins.

- RDS: single `db.t4g.micro` (ARM, AWS Free Tier eligible for 12 months on most accounts — confirm current terms, Section 8.4), single-AZ for MVP, automated backups on.
- DynamoDB: on-demand capacity mode, no provisioned-throughput tuning needed at this scale.

### 3.5 Inter-Service Communication (No Discovery Server)

With no Eureka, any service that needs to call another (now only across the 5 boundaries — most former inter-service calls are in-process per Section 3.3) resolves its base URL from an environment variable:
```
ATTENDANCE_SERVICE_URL=http://localhost:8082   # co-located on one EC2
ATTENDANCE_SERVICE_URL=http://10.0.1.23:8082   # if later split to its own instance
```
- **Prefer events over synchronous calls** wherever eventual consistency is acceptable (e.g. attendance-service doesn't need a live call to identity-service for an employee's name on every request — it consumes `employee.updated` and keeps a small local read-model of just the fields it needs).
- **Synchronous REST calls are fine** for request-time authorization checks (e.g. worklife-service confirming a manager's `departmentIds` against identity-service at task-assignment time), but keep the call count per request low and add timeouts + circuit-breaking (Resilience4j).
- This pattern is what makes future scaling painless: if attendance-service gets hot, move it to its own EC2, update its systemd/Nginx target and the `ATTENDANCE_SERVICE_URL` env var everywhere it's referenced — no code change.

---

## 4. Security & RBAC

### 4.1 Roles
Two orthogonal concepts — do not conflate them:
- **Org Hierarchy (display/reporting only):** CEO, CTO, CFO, HR, Senior Manager, Manager, Team Lead, Associate, Analyst, Associate Analyst.
- **Access Roles (authorization):** `ADMIN`, `HR`, `MANAGER`, `EMPLOYEE`.

Every user has exactly one Access Role used by `@PreAuthorize`, and one Org Title used only for display/org-chart purposes.

### 4.2 JWT Claims (issued by identity-service's auth module)
```json
{
  "sub": "userId",
  "role": "MANAGER",
  "departmentIds": [3, 7],
  "employeeId": 1042,
  "iat": ...,
  "exp": ...
}
```
- There is no gateway validating tokens — Nginx forwards the `Authorization` header untouched. **Every service validates the JWT signature and expiry itself**, via a shared `huvo-security-lib` dependency (in-process, not a network call).
- Each service also re-validates `role` + `departmentIds` for its own authorization decisions.
- Access/refresh token pattern: short-lived access token (15 min), refresh token (7 days), httpOnly cookie or secure mobile storage.

### 4.3 Secrets
Never commit DB passwords, JWT signing keys, or SMTP/SES credentials in plaintext for the `ec2-prod` profile. Use **AWS Systems Manager Parameter Store** (SecureString); the EC2 instance's IAM role reads these at boot and writes them into each service's systemd `EnvironmentFile` before starting it. Local profile uses a gitignored local properties/`.env` file with dev-only, non-sensitive values (Section 8.1).

---

## 5. Attendance Engine — Detailed Business Rules

This is the highest-risk, highest-value logic in the system. Implement as a dedicated rules-engine package inside **attendance-service** (kept standalone specifically for this reason — see Section 3.1), fully unit-tested, not scattered across controllers.

### 5.1 Data Model (core tables, schema `huvo_attendance`)
- `shift(id, name, start_time, end_time, grace_minutes DEFAULT 15)`
- `roster(employee_id, shift_id, effective_from, effective_to)`
- `company_working_days(company_id, day_of_week)` — configurable (Mon–Fri or Mon–Sat)
- `login_event(id, employee_id, login_timestamp, source_ip, device)` — **immutable, append-only**, the raw fact table
- `attendance_day(id, employee_id, date, status ENUM[PRESENT, LATE, ABSENT, ON_LEAVE, HOLIDAY], first_login_at, is_auto_marked BOOLEAN, reason_note)`
- `late_tracker(employee_id, week_start_date, late_days_count, consecutive_late_days_count)`

`employee_id` is a foreign key **within this schema**, kept in sync via the `employee.created`/`employee.updated` events from identity-service (attendance-service does not query identity-service's schema directly — Section 3.4 rule).

### 5.2 Algorithm (triggered on every login event)
1. On successful login (any role, CEO → Associate Analyst), identity-service's auth module publishes `user.login.success` → attendance-service consumes it.
2. Resolve the employee's active `roster` → `shift.start_time`.
3. If `attendance_day` for today doesn't exist, create it with `first_login_at = now()`.
4. Compute `delta = login_timestamp - shift.start_time`.
5. **If `delta > grace_minutes` (default 15):**
   a. Mark `attendance_day.status = LATE`.
   b. Publish `attendance.late.detected` → notify-service sends the late-warning email **immediately** (no batching).
   c. Increment `late_tracker.consecutive_late_days_count` (reset to 0 if yesterday wasn't late) and `late_tracker.late_days_count` for the current configured week.
6. **Evaluate auto-absent condition** (after incrementing counters):
   - `consecutive_late_days_count >= 3` **OR** `late_days_count (this week) >= 2` →
     - Override `attendance_day.status = ABSENT`, `is_auto_marked = true`.
     - Publish `attendance.autoAbsent.triggered` → notify-service emails the employee **and** manager/HR (configurable recipients).
     - Reset `consecutive_late_days_count` to 0 after triggering (avoid re-triggering daily) — confirm this reset policy with the product owner; documented here as an assumption.
7. Week boundary/"working days" respect `company_working_days`; a scheduled job resets `late_tracker` at the start of each configured week.
8. All status changes go through this engine — a manual admin override is allowed but must write to `login_event`/`attendance_day` and to `huvo_audit_log` (via `huvo-audit-client`), never a silent overwrite.

### 5.3 Edge Cases
- Multiple logins in a day → only the first counts for lateness; all are logged for audit.
- Remote vs office — same rule; `source_ip`/geolocation is metadata only in v1, not enforced.
- Approved leave that day → `status = ON_LEAVE`, login still logged, lateness rules skipped.
- Company holiday → `status = HOLIDAY`, skip rules.

---

## 6. Task Management — Business Rules

Lives inside **worklife-service** as the `task` domain package (Section 3.3).

### 6.1 States
`ASSIGNED → IN_PROGRESS → SUBMITTED → COMPLETED`, plus flags `OVERDUE` (deadline passed, not submitted) and `LATE_SUBMITTED` (submitted after deadline).

### 6.2 Rules
- A manager can assign tasks only to employees within their `departmentIds` (enforced server-side — worklife-service calls identity-service to verify, or relies on the `departmentIds` JWT claim plus a fresh check for high-stakes actions).
- File submission uses **S3 pre-signed URLs** — the client uploads directly to S3; the task module only stores the resulting object key/metadata.
- A scheduled job (every 15 min is sufficient) marks tasks with `deadline < now()` and status not in `(SUBMITTED, COMPLETED)` as `OVERDUE`, publishes `task.overdue` → notify-service.
- Manager dashboard reads should be backed by a query optimized for department-wise grouping; consider a materialized summary table refreshed via events if live aggregation becomes slow.

---

## 7. Event Catalog (RabbitMQ)

**RabbitMQ stays self-hosted, single node**, on the backend EC2. Future option once real production traffic justifies it: **Amazon SNS + SQS** (pay-per-use, zero broker maintenance) — a deliberate later migration, not a default right now.

One topic exchange per bounded context (`attendance.exchange`, `task.exchange`, `leave.exchange`, `identity.exchange`, etc.).

| Event | Publisher | Consumers |
|---|---|---|
| `user.login.success` | identity-service | attendance-service |
| `employee.created` / `employee.updated` / `employee.deleted` | identity-service | attendance-service, worklife-service, payroll-service |
| `department.created` / `department.updated` / `department.deleted` | identity-service | attendance-service, worklife-service, payroll-service (future) |
| `attendance.late.detected` | attendance-service | notify-service |
| `attendance.autoAbsent.triggered` | attendance-service | notify-service, payroll-service (future) |
| `task.assigned` | worklife-service | notify-service |
| `task.overdue` | worklife-service | notify-service |
| `task.submitted.late` | worklife-service | notify-service |
| `leave.applied` / `leave.approved` / `leave.rejected` | worklife-service | notify-service, attendance-service |
| `document.expiring` | worklife-service | notify-service |
| `payroll.generated` | payroll-service | notify-service |

Every service writes its own `huvo_audit_log` entry directly (via `huvo-audit-client`) for the actions it takes — audit is a library call, not a queue consumer.

Event envelope:
```json
{
  "eventId": "uuid",
  "eventType": "attendance.late.detected",
  "occurredAt": "ISO-8601",
  "producedBy": "attendance-service",
  "payload": { }
}
```
Every consumer must be **idempotent** (dedupe on `eventId`) since RabbitMQ delivery is at-least-once.

---

## 8. Environments — Local Development and AWS, Both Without Docker

### 8.1 Local Development (No Docker)
Every developer runs the real dependencies natively — no containers, anywhere:
- **MySQL:** install natively (Homebrew, `apt`, or the MySQL installer on Windows). One local MySQL instance, 4 schemas, matching Section 3.4 — use the same Flyway migrations that run in prod.
- **RabbitMQ:** install natively via the platform's package manager and run it as a local service.
- **DynamoDB & S3:** do **not** emulate these locally (no DynamoDB Local, no MinIO — those typically run via Docker and add behavioral drift). Instead, provision a small set of **real, low-cost AWS dev resources** (`huvo-dev-notifications`, `huvo-dev-chat-messages`, `huvo-dev-audit-log` DynamoDB tables on-demand; `huvo-dev-assets` S3 bucket) under a dedicated dev IAM user, used via every developer's local `AWS_PROFILE`. Cost at this scale is negligible.
- **Running services:** each of the 5 services is started individually — `mvn spring-boot:run -Dspring-boot.run.profiles=local` (or the IDE equivalent) — each bound to its port from Section 3.2. Nginx isn't required for day-to-day local dev (call each service directly); install it natively too if you want full routing parity before a deploy.
- **Local config:** a gitignored `application-local.yml` per service pointing at local MySQL/RabbitMQ and the `huvo-dev-*` AWS resources — never real production credentials.

### 8.2 AWS Deployment Architecture (Deploy-Ready, CloudFormation to Follow)
- **Backend EC2:** `t4g.medium` (ARM/Graviton — roughly 15–20% cheaper on-demand than the equivalent `t3.medium`, and Corretto/Temurin run fine on ARM). Runs Nginx (path-based routing) and the 5 systemd-managed services plus RabbitMQ.
- **Frontend EC2:** `t3.micro` — kept on the x86 family since this is the SKU most reliably covered by the classic Free Tier; no need to complicate the cheapest, smallest instance in the stack.
- **RDS MySQL** `db.t4g.micro` (already ARM), 4 schemas.
- **DynamoDB** 3 tables, on-demand.
- **S3** one bucket, prefixed (`documents/`, `task-submissions/`, `payslips/`, `chat-attachments/`).

This is exactly what the CloudFormation template (a separate, later deliverable) will provision: VPC/security groups, 2 EC2 instances with `UserData` bootstrap (install JRE, create systemd units, pull initial JARs from S3), IAM instance roles (S3 + DynamoDB + SSM Parameter Store + CloudWatch Logs, least privilege), 2 Elastic IPs, the RDS instance, the 3 DynamoDB tables, the S3 bucket, and a CloudWatch Log Group.

### 8.3 JVM & Process Tuning (Cost Lever)
- Set explicit, conservative heap sizes per service rather than letting the JVM auto-size against total host memory — these are lightweight CRUD services, not memory-hungry: `-Xms256m -Xmx384m` in each systemd unit's `ExecStart`/`EnvironmentFile`.
- Enable `spring.main.lazy-initialization=true` to reduce startup memory and time.
- With this tuning, the 5 services average roughly 350–450MB resident each (~2–2.2GB total) plus RabbitMQ (~200–300MB) — comfortably within `t4g.medium`'s 4GB with real headroom. Don't drop to `t4g.small` (2GB) unless you're actively monitoring closely; it's too tight to be safe as a default.

### 8.4 Free Tier Reality Check
- Classic AWS Free Tier (12 months from account creation) reliably covers `t2.micro`/`t3.micro` EC2 and `db.t2.micro`/`t3.micro`/`t4g.micro` RDS at 750 hrs/month — the **frontend EC2 and the RDS instance** both qualify.
- The **backend EC2 (`t4g.medium`) does not qualify** for free tier at any point — it's above the covered instance size, so it's a real cost from day one.
- DynamoDB's "Always Free" tier (25GB storage + 25 RCU/WCU) is **not time-limited** — likely covers your early chat/notification/audit usage indefinitely at this scale.
- AWS has adjusted Free Tier terms for newer accounts (credit-based offers alongside/instead of the classic 12-month structure) — confirm current terms for your specific account on AWS's Free Tier page before finalizing budget assumptions.

### 8.5 Key Runtime Decisions
- **No hardcoded localhost/IPs.** Every inter-service URL and AWS resource endpoint comes from an environment variable (Sections 3.5, 4.3).
- **Health checks:** every service exposes Spring Boot Actuator `/actuator/health`. `systemd` (`Restart=on-failure`) handles process-level restart-on-crash.
- **Logging:** structured JSON logs (Logback JSON encoder) to `/var/log/huvo/<service>.log`, tailed by the CloudWatch agent, rotated via `logrotate`.
- **Security Groups:** backend EC2 accepts inbound only from the frontend EC2's security group (API traffic) plus SSM (no open SSH to the world); RDS accepts inbound only from the backend security group.

---

## 9. Deployment & CI/CD (No Docker)

- Each service builds a single executable JAR (`spring-boot-maven-plugin` repackage, `mvn clean package`) — no Dockerfiles anywhere in the repo.
- Each service reads its port from `SERVER_PORT` (defaults per Section 3.2) and binds to `127.0.0.1` — only Nginx, on the same box, needs to reach it directly.
- Each service has a systemd unit, e.g. `/etc/systemd/system/huvo-attendance-service.service`:
```ini
[Unit]
Description=Huvo Attendance Service
After=network.target

[Service]
User=huvo
EnvironmentFile=/etc/huvo/attendance-service.env
ExecStart=/usr/bin/java -Xms256m -Xmx384m -jar /opt/huvo/attendance-service.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```
- `EnvironmentFile` contents are generated at instance boot (CloudFormation `UserData`, once written) by pulling values from SSM Parameter Store — never committed to the repo.
- **Jenkins pipeline per service:** `checkout → unit test → mvn package → upload jar to S3 (huvo-<env>-artifacts/<service>/<gitSha>.jar) → AWS SSM Run Command triggers the target instance to download the new jar, replace /opt/huvo/<service>.jar, and systemctl restart huvo-<service>.service`.
- SSM Run Command instead of SSH means no open port 22, no SSH key management in Jenkins — access is controlled by IAM.
- Tag every uploaded JAR with the git SHA in its S3 key — never overwrite a single `latest.jar` — so rollback is re-running the SSM command against the previous key.
- Nginx config is a versioned file in the repo, deployed the same way (SSM Run Command copies it and reloads Nginx) — never hand-edited on the box.

---

## 10. Coding Conventions

- Java package root per service: `com.huvo.<servicename>` (confirm the existing Employee/Department services' actual `groupId` and Java/Spring Boot version before introducing a new one, to stay consistent).
- Layered structure per domain package (Section 3.3): `controller/ → service/ → repository/ → dto/ → entity/ → event/ → config/ → exception/`.
- DTOs at API boundaries always — never expose JPA entities directly in responses.
- Global exception handling via `@ControllerAdvice`, consistent error shape:
```json
{ "timestamp": "...", "status": 404, "error": "NOT_FOUND", "message": "...", "path": "..." }
```
- Every table gets a Flyway migration (`V{n}__description.sql`) — schema only, per the no-mock-data rule in Section 2.1. No `hibernate.ddl-auto=update` beyond `local`.
- Shared code lives in small, independently versioned local modules (not services): `huvo-security-lib` (JWT validation), `huvo-audit-client` (writes to `huvo_audit_log`), `huvo-event-contracts` (shared event DTOs so publishers and consumers don't drift). Each service depends on these as regular Maven dependencies (`mvn install` locally, or a lightweight internal artifact repo later if the team grows).
- Scheduled jobs must be idempotent and safe to run on a single instance (no distributed lock needed yet — leave a `// TODO: ShedLock if scaled horizontally` comment where relevant).

---

## 11. Repository Layout (`huvo-backend`)

```
huvo-backend/
  identity-service/
  attendance-service/
  worklife-service/
  payroll-service/
  notify-service/
  common-libs/
    huvo-security-lib/
    huvo-audit-client/
    huvo-event-contracts/
  nginx/
    huvo-backend.conf
  infra/
    (CloudFormation templates — added in a later step)
```
Each service folder is independently buildable (`mvn clean package` inside it) and independently deployable via its own Jenkins pipeline stage — not modules of one Maven reactor build, so one service's build failure never blocks another's deploy.

---

## 12. Phased Roadmap

1. **Phase 1 — Platform Skeleton:** identity-service (auth + employee + department, folding in the existing services) with JWT + `huvo-security-lib`; Nginx routing config for all 5 services.
2. **Phase 2 — Attendance Engine:** the algorithm in Section 5, fully tested, dashboard read APIs, kept in its own service per Section 3.1.
3. **Phase 3 — Worklife (Task + Leave + Onboarding):** task assignment, S3 submissions, deadline scheduler, leave application/approval, document vault, manager dashboard APIs.
4. **Phase 4 — Payroll (basic):** salary structure, payslip generation, linked to attendance/leave via events.
5. **Phase 5 — notify-service:** email (SES) + in-app notifications, then chat (WebSocket + DynamoDB), lowest priority per the stated feature order.
6. **Phase 6 — IaC:** generate the CloudFormation template covering everything in Section 8.2 (explicitly deferred — ask for it once Phases 1–3 are stable enough to know real resource sizing).
7. **Phase 7 — AWS Hardening:** Multi-AZ RDS if warranted, ALB + TLS, CloudWatch alarms, backup verification.

---

## 13. Open Questions to Resolve Before/During Build

- Confirm existing Employee/Department services' Java version, Spring Boot version, and Maven `groupId` so identity-service stays consistent as they're folded in.
- Confirm whether "auto-absent" resets the consecutive-late counter to 0 after triggering, or continues counting (Section 5.2 states an assumption) — validate with the product owner.
- Confirm SES sandbox vs production access plan (SES starts in sandbox; production access has a review lead time — request it early).
- Confirm if e-sign is a real integration (e.g. DocuSign API) or a simple "typed name + timestamp + IP" acknowledgment for v1.
- Confirm single-region deployment is acceptable for MVP.
- Confirm the dev AWS account/IAM setup for the `huvo-dev-*` resources referenced in Section 8.1.
- Confirm current AWS Free Tier terms for the actual account being used (Section 8.4) before finalizing budget assumptions.
