# Huvo

Production-grade **HR + Work Management platform** for 10–200 employee companies.
Attendance automation, task visibility, leave, payroll, and internal chat — with a
dark-mode-first, Linear-grade UX.

## What it is

Huvo covers the full employee lifecycle — **Org Structure → Onboarding →
Attendance → Tasks → Leave → Payroll → Offboarding**, plus **internal chat**.
Two priorities drive every engineering trade-off:

1. **Attendance automation correctness** (login-triggered, late-tracking,
   auto-absent rules) — the core differentiator. Bulletproof and auditable.
2. **Task visibility** — real-time manager dashboards, no stale state.

Hard rules across the whole project: **no Docker anywhere** (plain JARs + static
SPA bundle), **no mock/demo data** in product code, environment-driven config.

## Layout

```text
Huvo/                  ← this repo (backend + docs; frontend has its own history)
├── Huvo-backend/      ← all microservices (per Huvo_Backend_Context.md)
└── Huvo-frontend/     ← git-ignored here; separate repo, own EC2
    (per Backend Context §0 — it has, or will have, its own git history)
```

## Core modules

| Area | Backend service | Frontend feature |
|---|---|---|
| Auth + employees + departments | `identity-service` (folded-in employee/department services, JWT) | `auth/`, `organization/` (full CRUD against live endpoints) |
| Attendance engine + dashboards | `attendance-service` (isolated — spiky, differentiator-critical load) | `attendance/`, role-aware bento `dashboard/` |
| Tasks + leave + onboarding/docs | `worklife-service` | `tasks/` (kanban/list, optimistic updates), `leave/`, `onboarding/` |
| Salary + payslips | `payroll-service` | `payroll/` |
| Email + in-app notifications + chat WS | `notify-service` | `chat/`, notifications feed |
| Cross-cutting | `common-libs/` (`huvo-security-lib`, `huvo-audit-client`, `huvo-event-contracts`), `nginx/huvo-backend.conf`, `infra/` (CloudFormation — later step) | Cmd+K palette, single multiplexed WebSocket, settings |

Full specs: `Huvo-backend/Huvo_Backend_Context.md` (architecture, §3 load-grouped
5-service split, §5 attendance algorithm, §8 AWS, §9 Jenkins JAR deploys, §12 roadmap)
and `Huvo-frontend/Huvo_Frontend_Context.md` (design system §4, API/WS contracts
§5, all 10 modules §6, quality bar §9, structure §10).

## Tech stack

- **Backend:** Java 17, Spring Boot 3, MySQL (one schema per service:
  `huvo_<service>`), Flyway (schema-only migrations), RabbitMQ (self-hosted,
  single node), SES (email), S3 (assets + JAR artifacts), DynamoDB (chat),
  Nginx path-based routing (no Gateway/Eureka/Config Server), Jenkins →
  S3 → SSM Run Command → systemd (`huvo-<service>.service`) on one Backend EC2.
- **Frontend:** React 18 + Vite + strict TypeScript, Tailwind tokens
  (dark hero + complete light mode), TanStack Query + Zustand, single
  Bearer-fetch client with single-flight silent refresh, one multiplexed
  WebSocket with exponential backoff, cmdk palette, Vaul mobile nav, RHF + Zod,
  Recharts, Lucide. `npm run build` → static `dist/` served by Nginx on its own
  Frontend EC2. No literal URLs in source (`VITE_API_BASE_URL`, `VITE_WS_URL`).

## Current build status

- **Backend:** Phase 1 skeleton live — `identity-service` (auth + employee +
  department, JWT via `huvo-security-lib`), Nginx routing for all 5 services,
  Jenkins pipeline (test → JAR → S3 → SSM deploy). Phases 2–5
  (attendance engine, worklife, payroll, notify/chat) per Context §12.
- **Frontend:** complete per spec and verified — `tsc --noEmit` 0 errors,
  `npm run build` green with per-route code-splitting. Every screen hits the
  real API with honest loading/empty/error states; service-backed pages outside
  identity show proper until-deployed states. One known spec routing mismatch
  (Context §5.1 `/api/identity/*` vs deployed `/api/auth|employees|…`) is
  followed on the Nginx side and isolated in `src/shared/api/endpoints.ts`.
- **Environments:** local dev via `.env.development` (backend ports 8081–8085);
  EC2 prod via `.env.ec2-prod` + `npm run build:prod`.
