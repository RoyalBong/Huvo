import { lazy, Suspense, type ReactNode } from 'react';
import { Link, Outlet, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/layouts/AppLayout';
import { AuthLayout } from '@/layouts/AuthLayout';
import { GuestOnly, RequireAuth, RequireRoles } from '@/app/guards';
import { Skeleton } from '@/shared/ui/Skeleton';
import { EmptyState } from '@/shared/ui/States';
import { Button } from '@/shared/ui/Button';
import { Home } from 'lucide-react';

/* Feature modules — code-split per route for a fast first load (§8). */
const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'));
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'));
const AttendancePage = lazy(() => import('@/features/attendance/AttendancePage'));
const TasksPage = lazy(() => import('@/features/tasks/TasksPage'));
const LeavePage = lazy(() => import('@/features/leave/LeavePage'));
const PayrollPage = lazy(() => import('@/features/payroll/PayrollPage'));
const ChatPage = lazy(() => import('@/features/chat/ChatPage'));
const EmployeesPage = lazy(() => import('@/features/organization/EmployeesPage'));
const DepartmentsPage = lazy(() => import('@/features/organization/DepartmentsPage'));
const OnboardingPage = lazy(() => import('@/features/onboarding/OnboardingPage'));
const SettingsPage = lazy(() => import('@/features/settings/SettingsPage'));

/** Route-level loading state shaped like a real page (§9). */
function PageFallback() {
  return (
    <div className="space-y-5" aria-busy aria-label="Loading">
      <Skeleton className="h-7 w-56" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton className="h-36 rounded-card" />
        <Skeleton className="h-36 rounded-card" />
        <Skeleton className="h-36 rounded-card" />
      </div>
      <Skeleton className="h-64 rounded-card" />
    </div>
  );
}

function Page({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageFallback />}>{children}</Suspense>;
}

function NotFound() {
  return (
    <EmptyState
      icon={Home}
      title="Page not found"
      description="The page you're looking for doesn't exist or has moved."
      action={
        <Button asChild variant="secondary">
          <Link to="/">Back to dashboard</Link>
        </Button>
      }
    />
  );
}

/** Signed-in shell: auth guard + app layout + routed module content. */
function AppShell() {
  return (
    <RequireAuth>
      <AppLayout>
        <Outlet />
      </AppLayout>
    </RequireAuth>
  );
}

const MANAGEMENT_ROLES = ['ADMIN', 'HR', 'MANAGER'] as const;
const PAYROLL_ROLES = ['ADMIN', 'HR', 'EMPLOYEE'] as const;

export function AppRoutes() {
  return (
    <Routes>
      {/* Signed-out */}
      <Route
        path="/login"
        element={
          <GuestOnly>
            <Page>
              <AuthLayout>
                <LoginPage />
              </AuthLayout>
            </Page>
          </GuestOnly>
        }
      />
      <Route
        path="/register"
        element={
          <GuestOnly>
            <Page>
              <AuthLayout>
                <RegisterPage />
              </AuthLayout>
            </Page>
          </GuestOnly>
        }
      />

      {/* Signed-in app shell */}
      <Route element={<AppShell />}>
        <Route path="/" element={<Page><DashboardPage /></Page>} />
        <Route path="/attendance" element={<Page><AttendancePage /></Page>} />
        <Route path="/tasks" element={<Page><TasksPage /></Page>} />
        <Route path="/leave" element={<Page><LeavePage /></Page>} />
        <Route
          path="/payroll"
          element={
            <RequireRoles roles={PAYROLL_ROLES}>
              <Page><PayrollPage /></Page>
            </RequireRoles>
          }
        />
        <Route path="/chat" element={<Page><ChatPage /></Page>} />
        <Route
          path="/organization/employees"
          element={
            <RequireRoles roles={MANAGEMENT_ROLES}>
              <Page><EmployeesPage /></Page>
            </RequireRoles>
          }
        />
        <Route
          path="/organization/departments"
          element={
            <RequireRoles roles={MANAGEMENT_ROLES}>
              <Page><DepartmentsPage /></Page>
            </RequireRoles>
          }
        />
        <Route path="/onboarding" element={<Page><OnboardingPage /></Page>} />
        <Route path="/settings" element={<Page><SettingsPage /></Page>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

