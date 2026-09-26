# Huvo Frontend

The Huvo SPA — React 18 + Vite + strict TypeScript, built per `Huvo_Frontend_Context.md`
(dark-mode-first, CSR only, **no Docker anywhere**).

## Commands

```bash
npm install
npm run dev        # local dev (uses .env.development → backend ports 8081-8085)
npm run build      # type-check + production bundle → dist/
npm run build:prod # same, but with --mode ec2-prod (.env.ec2-prod — gitignored)
npm run preview    # serve dist/ locally
```

## Environment

All API/WebSocket URLs are environment-driven (`VITE_API_BASE_URL`, `VITE_WS_URL`, optional
per-service overrides) — no literal URLs in source (§5.2, §11). See `.env.example` and
`.env.ec2-prod.example`. Missing config surfaces an honest toast at boot instead of failing
silently.

## Layout

```
src/
├── app/           # App shell, providers, router, role guards, nav config
├── features/      # auth, dashboard, attendance, tasks, leave, payroll,
│                  # chat, organization, onboarding, settings
├── shared/
│   ├── api/       # fetch client (single-flight silent refresh) + TanStack hooks per prefix
│   ├── command-palette/  # cmdk + global action registry (§6.11)
│   ├── realtime/  # ONE WebSocket, multiplexed channels, exponential backoff (§5.3)
│   ├── ui/        # design-system primitives (§4)
│   ├── hooks/, utils/, types/
├── layouts/       # AppLayout (sidebar + glass header + mobile bottom sheet), AuthLayout
└── styles/        # design tokens — dark hero, light equally complete
```

## Hard rules

- **No mock data anywhere** — every screen hits the real API and shows honest
  loading/empty/error states (§7).
- Backend routing follows `huvo-backend/nginx/huvo-backend.conf`
  (`/api/auth|employees|departments|attendance|tasks|leave|documents|payroll|notify`).
  See the note in `src/shared/api/endpoints.ts` about the §5.1 prefix discrepancy.
