/**
 * Every backend path the frontend can call, mirroring the deployed Nginx
 * reverse-proxy routing (huvo-backend/nginx/huvo-backend.conf).
 *
 * NOTE: Huvo_Frontend_Context.md §5.1 lists `/api/identity/*`, `/api/worklife/*`
 * style prefixes, but the versioned Nginx config actually routes
 * `/api/auth/`, `/api/employees/`, `/api/departments/`, `/api/attendance/`,
 * `/api/tasks|leave|documents/`, `/api/payroll/`, `/api/notify/` — the Nginx
 * config is what is deployed, so it wins. If §5.1 is ever reconciled to match,
 * only this file and the env overrides change.
 */
export const endpoints = {
  // identity-service (8081)
  auth: {
    login: '/api/auth/login',
    register: '/api/auth/register',
    refresh: '/api/auth/refresh',
    logout: '/api/auth/logout',
    password: '/api/auth/password',
  },
  employees: '/api/employees',
  departments: '/api/departments',

  // attendance-service (8082)
  attendance: {
    today: '/api/attendance/today',
    history: '/api/attendance/history',
    dashboard: '/api/attendance/dashboard',
    lateTracker: '/api/attendance/late-tracker',
    shifts: '/api/attendance/shifts',
  },

  // worklife-service (8083)
  tasks: '/api/tasks',
  taskMine: '/api/tasks/mine',
  taskTeam: '/api/tasks/team',
  leave: '/api/leave',
  leaveBalance: '/api/leave/balance',
  documents: '/api/documents',
  onboarding: '/api/documents/onboarding',

  // payroll-service (8084)
  payroll: {
    payslips: '/api/payroll/payslips',
    structure: '/api/payroll/structure',
  },

  // notify-service (8085)
  notify: {
    notifications: '/api/notify/notifications',
    conversations: '/api/notify/chat/conversations',
    messages: (conversationId: string) => `/api/notify/chat/${conversationId}/messages`,
    /** WebSocket endpoint (§5.3) — value comes from VITE_WS_URL. */
    ws: '/api/notify/ws',
  },
} as const;
