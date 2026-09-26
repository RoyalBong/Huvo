/** Central query-key registry — one tree per backend path prefix (§5.1). */
export const queryKeys = {
  identity: {
    employees: () => ['employees'] as const,
    employee: (id: number) => ['employees', id] as const,
    departments: () => ['departments'] as const,
    department: (id: number) => ['departments', id] as const,
  },
  attendance: {
    today: () => ['attendance', 'today'] as const,
    history: (from?: string, to?: string) => ['attendance', 'history', from, to] as const,
    dashboard: (from?: string, to?: string) => ['attendance', 'dashboard', from, to] as const,
    lateTracker: () => ['attendance', 'late-tracker'] as const,
    shifts: () => ['attendance', 'shifts'] as const,
  },
  worklife: {
    myTasks: () => ['tasks', 'mine'] as const,
    teamTasks: () => ['tasks', 'team'] as const,
    allTasks: () => ['tasks', 'all'] as const,
    leaveRequests: () => ['leave', 'requests'] as const,
    leaveBalance: () => ['leave', 'balance'] as const,
    onboarding: () => ['onboarding', 'checklist'] as const,
  },
  payroll: {
    payslips: () => ['payroll', 'payslips'] as const,
    structure: () => ['payroll', 'structure'] as const,
  },
  notify: {
    notifications: () => ['notify', 'notifications'] as const,
    conversations: () => ['notify', 'conversations'] as const,
    messages: (conversationId: string) => ['notify', 'messages', conversationId] as const,
  },
} as const;
