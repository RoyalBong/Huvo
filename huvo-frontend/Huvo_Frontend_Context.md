# Huvo Frontend Context

> Single source of truth for building `huvo-frontend`. Read fully before generating or modifying any frontend code. Aligned with `Huvo_Backend_Context.md` — the 5-service, no-Docker, cost-optimized backend architecture. Where this document and an earlier draft disagree, this one wins.

---

## 0. Project Identity

- **Product Name:** Huvo (Human + Novo)
- **Type:** Production-grade HR + Work Management Platform for 10–200 employee companies
- **Rendering:** Client-Side Rendering (CSR) only
- **Framework Restriction:** **React + Vite only. No Next.js, no Docker anywhere (local or deployed).**
- **Repository:** `huvo-frontend` — separate repo from `huvo-backend`, deployed to its own EC2.

---

## 1. Competitive Landscape (Why This Has to Be Different)

Before any design decision, know what exists and why it isn't good enough:

| Competitor | Where it actually stands |
|---|---|
| **Zoho People** | Functional and comprehensive, but consistently described as visually dated, cluttered in places, with navigation that requires digging through menus and a mobile app that lags behind the web version. |
| **BambooHR** | Clean and easy to learn, strong at onboarding — but conventional. Nothing about it is memorable or distinctive; it's the "safe" choice. |
| **Keka** | The design leader in the Indian market — modern, employee-experience-focused, clean OKR dashboards. Still a conventional light, corporate-SaaS look. |
| **HiBob** | The design leader globally — but it's a social-media-style interface (feeds, celebrations, vibrant colors) built for engagement, not a precision productivity tool. |
| **Rippling** | Modern but literally a grid of app icons — functional, not distinctive, no strong visual identity. |

**The gap:** not one of these has gone dark-mode-first, and none has the calm, precise, "developer tool" visual language of Linear, Raycast, or Vercel. That combination — genuine HR depth *plus* that level of design craft — doesn't exist yet in this category. That's what Huvo is building toward, and it's a real, defensible position, not just an aesthetic preference.

---

## 2. Vision & Design Philosophy

Extremely classy, precise, and premium — smooth, fast, and highly usable, never at the expense of clarity. It should feel like Linear or Raycast decided to build an HR tool, not like an HR tool trying to look modern.

### Visual Style Goals
- Precise and calm, not loud or decorative — every visual flourish must earn its place
- Restrained glassmorphism: reserved for headers, cards, modals, and navigation — **never** large blocks of text or dense data on a glass surface (readability and WCAG contrast come first; 2026 design consensus has moved away from blur-everything toward this exact restraint)
- Soft depth via layered surfaces and subtle shadows, not heavy skeuomorphism
- **Dark mode as the primary and most beautiful experience**, with an equally polished, non-afterthought light mode
- Motion that's fast and purposeful (150–300ms, spring-based) — no parallax, no 3D, no marketing-site gimmicks; this is a tool people open dozens of times a day, not a landing page
- A genuinely fast, keyboard-first power-user layer (Section 6.11) sitting underneath a beautiful, approachable surface for everyone else

### What This Deliberately Avoids
Being trend-chasing for its own sake would work against the product. Explicitly **not** using: neo-brutalism, claymorphism, heavy 3D/spatial parallax, Y2K/retro styling, or a "dopamine" color palette. These read as marketing-site or consumer-app trends and would undercut the trust a payroll/attendance system needs to project.

### Inspiration Blend
- **Linear** → the command palette, the sense of speed, the restraint
- **Raycast** → precision, keyboard-first interaction, calm dark surfaces
- **Vercel Dashboard** → dark aesthetic, data clarity, confident typography
- **Notion** → calm usability, approachable for non-technical HR/employee users

---

## 3. Tech Stack (Strict)

| Layer | Choice | Why |
|---|---|---|
| Framework | React 18 + Vite | Fast dev loop, no SSR complexity we don't need for a CSR-only internal tool |
| Language | TypeScript (strict mode) | Non-negotiable at this scope |
| Styling | Tailwind CSS + custom design tokens | Section 4 |
| Animations | Framer Motion | Spring-based, purposeful micro-interactions |
| State Management | Zustand + TanStack Query | Client state vs. server state kept cleanly separate |
| Real-time | Native WebSocket (single connection, see Section 5.3) | One socket, multiplexed by channel — matches the backend's lean, cost-conscious philosophy |
| Icons | Lucide React | Consistent, comprehensive, tree-shakeable |
| Forms | React Hook Form + Zod | Type-safe validation matching backend DTO shapes |
| Routing | React Router v6 | |
| UI Primitives | Radix UI (headless) + custom components | Accessibility for free, full visual control |
| **Command Palette** | `cmdk` | Now a baseline SaaS expectation, not a nice-to-have (Section 6.11) — Linear is the reference implementation |
| **Dense Data Tables** | TanStack Table | For employee directories, payslip history, audit logs — see Section 4.4 on where bento/cards are the *wrong* pattern |
| **Mobile Sheets** | Vaul (bottom sheets) | Bottom sheets are replacing heavy dropdown/modal patterns on mobile in 2026 SaaS — used for task detail, quick actions, filters on small screens |
| **Toasts** | Sonner | Clean, modern toast pattern, minimal visual noise |
| Charts | Recharts or Tremor | Attendance trends, task completion, headcount — lightweight, themeable |
| Date handling | date-fns | |
| HTTP Client | native `fetch` + TanStack Query | No axios dependency needed |

---

## 4. Design System Foundations

### 4.1 Color System (Dark-first)
- **Background:** Deep charcoal / near-black with a subtle blue undertone — never pure black (pure black kills the sense of depth glass surfaces need to read against)
- **Surfaces:** Layered glass panels with `backdrop-blur`, used per the restraint rule in Section 2 — cards, modals, sticky nav/header only
- **Primary:** Soft indigo / electric violet
- **Accent:** Cyan / teal for success & live/real-time indicators
- **Semantic:** Success (emerald), Warning (amber), Danger (rose), Info (sky)
- **Live-state color language:** a distinct, consistent visual treatment (a small animated dot/glow, not a full badge) for anything genuinely real-time — someone typing in chat, an attendance check-in landing, a task status flipping live on a manager's dashboard. This visual vocabulary should be used *only* for things that are actually live, so it stays meaningful.

### 4.2 Typography
- Primary: Inter or Geist
- Mono: JetBrains Mono — employee IDs, timestamps, audit log entries, payslip reference numbers
- A clear, restrained type scale — expressive typography is a 2026 trend elsewhere, but a payroll/attendance tool should favor legibility and hierarchy over expressiveness

### 4.3 Motion Principles
- Fast and snappy (150–300ms), spring-based for interactive elements
- Staggered reveals for lists/dashboards on first load only — never re-trigger on every re-render (a common cause of "cheap-feeling" animation)
- Respect `prefers-reduced-motion` everywhere, without exception
- Real-time updates (a task status changing, a new chat message) animate in with a subtle, fast transition — never a jarring layout jump

### 4.4 Layout Patterns — Use the Right One for the Content
This is a real distinction the 2026 SaaS design consensus draws, and it matters here:
- **Bento-style grids** — for the role-aware dashboard's summary widgets (Section 6.2): attendance status, task overview, leave balance, quick actions. This is exactly the content shape bento grids are good at: a handful of scannable, asymmetric-priority cards.
- **Standard structured layouts (tables, lists, kanban columns)** — for anything dense or tabular: the employee directory, audit log, payslip history, task boards, chat. Bento/card patterns actively break down for this kind of content (inconsistent card heights, harder to scan a column of values) — don't force it.
- **Command palette** — for navigation and actions across all 10+ modules once the app has real depth (Section 6.11), so the sidebar doesn't have to carry the whole weight of every feature down the road.

---

## 5. Backend Alignment (Critical — Matches the Current 5-Service Backend)

The frontend talks to the backend exclusively through the Nginx reverse proxy on the Backend EC2. **No hardcoded localhost, ports, or IPs anywhere** — everything below is environment-driven.

### 5.1 API Base Paths
| Frontend Feature | Backend Path Prefix | Service |
|---|---|---|
| Auth, Employees, Departments | `/api/identity/*` | identity-service |
| Attendance | `/api/attendance/*` | attendance-service |
| Tasks, Leave, Onboarding/Documents | `/api/worklife/*` | worklife-service |
| Payroll | `/api/payroll/*` | payroll-service |
| Notifications, Chat | `/api/notify/*` | notify-service |

### 5.2 Environment Configuration
- `VITE_API_BASE_URL` — the Nginx-fronted backend base URL, never hardcoded
- `VITE_WS_URL` — the single WebSocket endpoint (Section 5.3)
- Both injected at build time for a given environment (`local`, `ec2-prod`) — no environment-specific value ever committed as a literal in source.

### 5.3 Real-Time: One WebSocket Connection, Not Five
The frontend opens **a single WebSocket connection to `notify-service`**, not a separate socket per feature. notify-service is already the event/message hub on the backend (it consumes RabbitMQ events from every other service) — it's the natural place to also relay them to connected clients, and it means the frontend isn't managing five independent socket lifecycles, reconnection strategies, and auth handshakes. Channels are multiplexed over that one connection:
```
ws://<host>/ws/notify
  → channel: "attendance"      (live status changes, late/absent flags)
  → channel: "tasks"           (status changes, new assignments)
  → channel: "leave"           (approval/rejection updates)
  → channel: "chat"            (messages, presence, typing indicators)
  → channel: "notifications"   (the in-app notification feed itself)
```
Implement reconnection with exponential backoff and clear visual feedback (a small, honest "reconnecting…" state — never silently fail and show stale data as if it were live).

### 5.4 Auth
- JWT stored per Section 4.2 of the backend context (short-lived access token + refresh token, httpOnly cookie or secure storage) — every protected request sends `Authorization: Bearer <token>`.
- Role-based route protection matches the backend's Access Roles exactly: `ADMIN`, `HR`, `MANAGER`, `EMPLOYEE`. The frontend never invents its own role logic beyond what the JWT claims and the corresponding API responses actually allow.

---

## 6. Core Modules & UX Requirements

### 6.1 Authentication & Onboarding
- Elegant, minimal login/register screens — the first impression sets the entire product's credibility
- Role-based protected routes
- Guided first-time onboarding flow
- JWT + refresh token handling, silent refresh where possible (no jarring re-login mid-session)

### 6.2 Role-Aware Dashboard
- Distinct experiences for Admin, HR, Manager, Employee — not just permission-gated versions of the same screen, genuinely different priority content per role
- Bento-style summary widgets (Section 4.4): attendance status, task overview, leave balance, quick actions
- Real-time updates via the single WebSocket connection (Section 5.3) — a manager should see a task flip to "submitted" without refreshing

### 6.3 Attendance
- Status auto-marked by the backend engine on login — the frontend never computes attendance status itself, only displays it
- Shift & roster calendar, visually clear PRESENT / LATE / ABSENT / ON_LEAVE / HOLIDAY states
- Late-login history with the actual warning/auto-absent trail (transparency matters here — an employee should be able to see exactly why they were marked absent)
- Real-time attendance dashboard for managers, department-grouped

### 6.4 Task Management (Major Differentiator)
**Managers:**
- Assign tasks (server-enforced to their `departmentIds` — the UI reflects this, never offers assignment outside it)
- Deadlines & priority
- Real-time department/team board — Kanban and List views
- Live status of every team member, updating via WebSocket, not polling

**Employees:**
- Clean task list/board, mark complete + file upload via S3 pre-signed URLs (client uploads directly to S3, per the backend architecture — the frontend never proxies file bytes through the app server)
- Clear OVERDUE and LATE_SUBMITTED visual states — unambiguous, not just a subtle color shift
- Optimistic updates on status changes (Section 8), reconciled against the real event when it arrives

### 6.5 Leave Management
- Calendar-picker leave application
- Approval/rejection flows for managers
- Leave balance overview, calendar view
- Visually tied to attendance status where the two intersect (an approved leave day should read differently from a regular present day, consistently, everywhere it appears)

### 6.6 Payroll
- Salary structure view, payslip history + download
- Deliberately the *calmest, most trustworthy* screen in the product — sensitive financial data doesn't get glassmorphism or playful motion; clarity and restraint over polish here specifically

### 6.7 Internal Chat
- Real-time 1:1 and group/department chat over the shared WebSocket connection
- Presence indicators, typing indicators, file sharing
- Modern, clean messaging UI — not trying to be a social feed (that's HiBob's territory; Huvo stays focused)

### 6.8 Organization
- Department/Team/Role management
- Employee directory as a real structured table (Section 4.4 — not a bento grid of employee cards; needs to scan cleanly at 200 employees)
- Visibility scoped by access role + `departmentIds`, exactly matching backend authorization — never show data in the UI that the API wouldn't actually return

### 6.9 Onboarding Module
- Interactive checklist, S3 document upload
- Simple e-sign/acknowledgment flow (scope depends on the backend's open question on real e-sign vs. typed-acknowledgment — build for whichever the backend team confirms)

### 6.10 Supporting Features
- Global notifications (in-app feed + toast via Sonner)
- User profile & settings
- Audit log viewer (Admin/HR) — a real structured table, not cards
- Responsive sidebar navigation, collapsing to bottom-sheet-driven mobile navigation (Section 3, Vaul) rather than a hamburger dumping the full desktop menu onto a small screen

### 6.11 Command Palette (Cmd+K) — Required, Not Optional
This was listed as "recommended for power users" in the earlier draft — promote it to required. With 10 feature areas across 4 roles, a sidebar alone won't scale gracefully, and command palettes are now the expected pattern in serious SaaS products, not a power-user extra. Every meaningful action should be reachable through it: navigate to any module, create a task, apply for leave, jump to an employee's profile, search chat. Linear's implementation is the reference point for what "done well" looks like here.

---

## 7. No Mock Data — Hard Rule (Frontend)

Matching the backend's hard rule exactly, and just as non-negotiable:

- The app **always calls the real backend API**, in every environment. There is no "demo mode" that falls back to hardcoded fixture data when the API is unreachable or returns empty — that failure state gets a real, honest empty/error UI (Section 9), never a silent substitution of fake content.
- No hardcoded sample employees, tasks, attendance records, chat messages, or payslips anywhere in the shipped application code — not in initial state, not as Storybook-style "example" data leaking into production bundles, not as placeholder content left in "temporarily."
- Loading and empty states must be designed as first-class UI (Section 9), specifically *because* there's no mock data to lean on for a good-looking screenshot — the real states have to look good on their own.
- Component-level isolated development (e.g., building a single card component before its API integration exists) may use local, clearly-scoped fixture data *only within that component's own dev/test file* — it must never be reachable from the actual running application's routes.
- If a sales/demo environment is needed later, it's a separate deployment against a separate demo dataset (matching the backend's Section 2.1 rule) — never a frontend-side fallback baked into the real product.

---

## 8. UX & Interaction Principles

- Extremely smooth and fast; clear visual hierarchy; excellent information density without feeling crowded
- Delightful but restrained micro-interactions on hover, focus, loading, success
- Fully keyboard accessible — this isn't just a Section 6.11 feature, it's a baseline requirement throughout
- Mobile-first mindset while looking premium on desktop
- Excellent loading, empty, and error states — skeleton loaders that match the actual eventual layout (not generic gray boxes), since real content is the only content that will ever appear (Section 7)
- Optimistic updates where safe — tasks and chat especially — always reconciled against the authoritative real-time event, never left silently unresolved if the optimistic update turns out wrong

---

## 9. Quality Bar

- Production-ready code quality, strict TypeScript throughout
- Clean feature-based architecture (Section 10)
- Consistent design system — no one-off components that drift from the tokens in Section 4
- Accessible to a WCAG AA standard, including on glass surfaces (contrast-checked, not assumed)
- Beautiful dark mode as the hero experience, light mode equally complete
- No visual or technical debt, no mock data anywhere in the shipped app (Section 7)
- Fully environment-driven configuration for clean EC2 deployment

---

## 10. Application Structure

```
src/
├── app/                      # App shell, providers, router
├── features/                 # Domain modules (feature-based)
│   ├── auth/
│   ├── dashboard/
│   ├── attendance/
│   ├── tasks/
│   ├── leave/
│   ├── payroll/
│   ├── chat/
│   ├── organization/         # Employees, Departments, Roles
│   ├── onboarding/
│   └── settings/
├── shared/
│   ├── command-palette/      # cmdk implementation, global action registry
│   ├── realtime/             # the single WebSocket client (Section 5.3), channel subscriptions
│   ├── ui/                   # design-system primitives
│   ├── hooks/
│   ├── utils/
│   ├── types/
│   └── api/                  # TanStack Query hooks per backend path prefix (Section 5.1)
├── layouts/                  # AppLayout, AuthLayout, etc.
└── styles/                   # Global styles & design tokens
```

Frontend feature folders are organized by user-facing domain, not 1:1 with backend service boundaries — `tasks/`, `leave/`, and `onboarding/` are separate frontend features even though they share a single `worklife-service` on the backend (Section 5.1). That's intentional: the frontend structure should serve the user's mental model, the backend structure serves cost/load isolation, and Section 5.1's table is what maps one to the other.

---

## 11. Deployment Notes (No Docker)

- `npm run build` produces the static SPA bundle; no Dockerfile anywhere in this repo, matching the backend's no-Docker decision.
- Served by Nginx on the **Frontend EC2** — static files only.
- All API and WebSocket calls go to the Backend EC2 (or an ALB later) via the environment variables in Section 5.2 — never a literal URL in source.
- Local development: `npm run dev` against a `local` env file pointing at the backend's local ports (Section 8.1 of the backend context) — no containers involved on the frontend side either.
- Environment variables are baked in at build time (Vite's standard `import.meta.env` mechanism) — a separate build is produced per environment rather than trying to inject config at runtime into a static bundle.

---

**Final Instruction**
Design and build this frontend with the highest level of craft, grounded in what actually exists in this category today (Section 1) and what's genuinely working in serious SaaS product design right now (Section 2, Section 4.4) — not trend-chasing, and not a copy of Linear's aesthetic pasted onto an HR tool. The bar: an HR/ops person opens this and it feels like the best-designed tool they use all day, not the tool they tolerate because IT picked it.
