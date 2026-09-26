import {
  Building2,
  CalendarCheck,
  ClipboardList,
  LayoutDashboard,
  ListTodo,
  MessagesSquare,
  PlaneTakeoff,
  Settings,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import type { Role } from '@/shared/types/auth';

export interface NavItem {
  id: string;
  to: string;
  label: string;
  icon: LucideIcon;
  /** Roles that may see/navigate to this module — mirrors backend
   *  authorization exactly (frontend §5.4, §6.8). */
  roles: readonly Role[];
}

const ALL: readonly Role[] = ['ADMIN', 'HR', 'MANAGER', 'EMPLOYEE'];

/** The app's 10 feature areas (§6), role-scoped. */
export const NAV_ITEMS: readonly NavItem[] = [
  { id: 'dashboard', to: '/', label: 'Dashboard', icon: LayoutDashboard, roles: ALL },
  { id: 'attendance', to: '/attendance', label: 'Attendance', icon: CalendarCheck, roles: ALL },
  { id: 'tasks', to: '/tasks', label: 'Tasks', icon: ListTodo, roles: ALL },
  { id: 'leave', to: '/leave', label: 'Leave', icon: PlaneTakeoff, roles: ALL },
  // Payslips are personal: employees see their own, HR/Admin administer (§6.6).
  { id: 'payroll', to: '/payroll', label: 'Payroll', icon: Wallet, roles: ['ADMIN', 'HR', 'EMPLOYEE'] },
  { id: 'chat', to: '/chat', label: 'Chat', icon: MessagesSquare, roles: ALL },
  // Directory & org structure: management roles only (§6.8).
  { id: 'organization', to: '/organization/employees', label: 'Organization', icon: Building2, roles: ['ADMIN', 'HR', 'MANAGER'] },
  { id: 'onboarding', to: '/onboarding', label: 'Onboarding', icon: ClipboardList, roles: ALL },
  { id: 'settings', to: '/settings', label: 'Settings', icon: Settings, roles: ALL },
];

export function navItemsForRole(role: Role | null | undefined): NavItem[] {
  if (!role) return [];
  return NAV_ITEMS.filter((item) => item.roles.includes(role));
}
