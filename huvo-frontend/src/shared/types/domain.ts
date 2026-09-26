/**
 * Domain types for services beyond identity-service.
 *
 * identity-service is the only service implemented so far; everything below
 * matches the contracts specified in Huvo_Backend_Context.md (sections noted
 * per type) and is the frontend's assumption until that service ships. The UI
 * renders honest loading/empty/error states — never substitute data (frontend §7).
 */

/* ---------- Attendance (backend §5.1) ---------- */

export type AttendanceStatus = 'PRESENT' | 'LATE' | 'ABSENT' | 'ON_LEAVE' | 'HOLIDAY';

export interface AttendanceDay {
  id: number;
  employeeId: number;
  date: string; // ISO date
  status: AttendanceStatus;
  firstLoginAt: string | null;
  isAutoMarked: boolean;
  reasonNote: string | null;
}

/** Late-warning / auto-absent trail the employee can inspect (frontend §6.3). */
export interface LateTracker {
  employeeId: number;
  weekStartDate: string;
  lateDaysCount: number;
  consecutiveLateDaysCount: number;
}

/* ---------- Tasks (backend §6.1) ---------- */

export type TaskStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'SUBMITTED' | 'COMPLETED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Task {
  id: number;
  title: string;
  description: string | null;
  assigneeId: number;
  assigneeName?: string;
  departmentId: number | null;
  deadline: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  overdue: boolean;
  lateSubmitted: boolean;
  attachmentKey: string | null;
  createdAt: string;
}

export interface TaskInput {
  title: string;
  description?: string;
  assigneeId: number;
  departmentId?: number | null;
  deadline?: string | null;
  priority: TaskPriority;
}

/* ---------- Leave (frontend §6.5) ---------- */

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName?: string;
  type: string;
  fromDate: string;
  toDate: string;
  reason: string;
  status: LeaveStatus;
  decidedBy?: number | null;
  decidedAt?: string | null;
}

export interface LeaveBalance {
  type: string;
  total: number;
  used: number;
  remaining: number;
}

/* ---------- Onboarding (frontend §6.9) ---------- */

export interface OnboardingItem {
  id: number;
  title: string;
  completed: boolean;
  completedAt: string | null;
  documentKey: string | null;
}

/* ---------- Payroll (frontend §6.6) ---------- */

export interface Payslip {
  id: number;
  employeeId: number;
  period: string; // e.g. "2026-09"
  grossPay: number;
  deductions: number;
  netPay: number;
  documentKey: string | null;
  generatedAt: string;
}

export interface SalaryStructure {
  employeeId: number;
  base: number;
  allowances: number;
  deductions: number;
  ctc: number;
}

/* ---------- Notifications & Chat (notify-service) ---------- */

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
}

export interface ChatConversation {
  id: string;
  label: string;
  isGroup: boolean;
  memberIds: number[];
  lastMessageAt: string | null;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: number;
  senderName: string;
  body: string;
  sentAt: string;
}
