import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Building2,
  CalendarCheck,
  Command,
  ListTodo,
  PlaneTakeoff,
  Plus,
  Radio,
  Users,
} from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { Widget, WidgetBody } from '@/features/dashboard/Widget';
import { api } from '@/shared/api/client';
import { useTodayAttendance } from '@/shared/api/attendance';
import { endpoints } from '@/shared/api/endpoints';
import { useEmployee } from '@/shared/api/identity';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeaveBalance, useMyTasks, useTeamTasks } from '@/shared/api/worklife';
import { useCommandPalette } from '@/shared/command-palette/registry';
import { useChannel } from '@/shared/realtime/useChannel';
import { LiveDot } from '@/shared/realtime/ReconnectBanner';
import { useRealtimeStore } from '@/shared/realtime/socket';
import { Badge, type BadgeProps } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/States';
import { formatTime } from '@/shared/utils/format';
import { cn } from '@/shared/utils/cn';
import type { AttendanceStatus } from '@/shared/types/domain';

const STATUS_TONE: Record<AttendanceStatus, NonNullable<BadgeProps['tone']>> = {
  PRESENT: 'success',
  LATE: 'warning',
  ABSENT: 'danger',
  ON_LEAVE: 'info',
  HOLIDAY: 'neutral',
};

interface ActivityEntry {
  key: string;
  channel: string;
  event: string;
  at: string;
}

/**
 * Role-aware dashboard (§6.2): genuinely different priority content per role,
 * bento summary widgets (§4.4), real-time updates over the single WebSocket —
 * a manager sees a task flip without refreshing. No mock data anywhere (§7).
 */
export default function DashboardPage() {
  const claims = useAuthStore((s) => s.claims);
  const role = claims?.role;
  const setPaletteOpen = useCommandPalette((s) => s.setOpen);
  const queryClient = useQueryClient();

  const { data: employee } = useEmployee(claims?.employeeId ?? null);
  const name = employee?.name ?? (claims ? `Employee #${claims.employeeId}` : 'there');

  const isPeopleRole = role === 'ADMIN' || role === 'HR';
  const isManager = role === 'MANAGER';

  const today = useTodayAttendance();
  const myTasks = useMyTasks();
  const teamTasks = useTeamTasks(isManager); // only fires for MANAGER
  const balance = useLeaveBalance();

  const headcount = useQuery({
    queryKey: queryKeys.identity.employees(),
    queryFn: ({ signal }) => api.get<unknown[]>(endpoints.employees, signal),
    enabled: isPeopleRole,
    retry: 1,
  });
  const departments = useQuery({
    queryKey: queryKeys.identity.departments(),
    queryFn: ({ signal }) => api.get<unknown[]>(endpoints.departments, signal),
    enabled: isPeopleRole,
    retry: 1,
  });

  const [activity, setActivity] = useState<ActivityEntry[]>([]);

  const track = (channel: string, event: string) =>
    setActivity((prev) =>
      [
        { key: `${Date.now()}-${prev.length}-${event}`, channel, event, at: new Date().toISOString() },
        ...prev,
      ].slice(0, 8),
    );

  // Real-time reconciliation (§5.3, §8): events invalidate the exact caches
  // they affect, so the dashboard never shows stale data as if it were live.
  useChannel('attendance', (e) => {
    track('attendance', e.event);
    void queryClient.invalidateQueries({ queryKey: ['attendance'] });
  });
  useChannel('tasks', (e) => {
    track('tasks', e.event);
    void queryClient.invalidateQueries({ queryKey: ['tasks'] });
  });
  useChannel('leave', (e) => {
    track('leave', e.event);
    void queryClient.invalidateQueries({ queryKey: ['leave'] });
  });
  useChannel('notifications', (e) => track('notifications', e.event));

  const tasks = myTasks.data ?? [];
  const taskCounts = {
    ASSIGNED: tasks.filter((t) => t.status === 'ASSIGNED').length,
    IN_PROGRESS: tasks.filter((t) => t.status === 'IN_PROGRESS').length,
    SUBMITTED: tasks.filter((t) => t.status === 'SUBMITTED').length,
    COMPLETED: tasks.filter((t) => t.status === 'COMPLETED').length,
  };

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const todayLabel = new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });
  const rtOpen = useRealtimeStore((s) => s.status) === 'open';

  return (
    <div>
      <PageHeader
        title={`${greeting}, ${name}`}
        description={`${todayLabel} · ${role ?? ''}`}
        actions={
          <Button variant="secondary" size="sm" onClick={() => setPaletteOpen(true)}>
            <Command className="h-3.5 w-3.5" aria-hidden />
            Commands
          </Button>
        }
      />

      <div className="grid gap-4 md:grid-cols-12">
        {/* Attendance status */}
        <Widget
          title="Today's attendance"
          icon={CalendarCheck}
          className="md:col-span-4"
          action={
            <Link to="/attendance" className="text-xs font-medium text-primary-ink hover:underline">
              Open
            </Link>
          }
        >
          <WidgetBody
            isLoading={today.isLoading}
            error={today.error}
            onRetry={() => void today.refetch()}
            isEmpty={!today.data}
            emptyTitle="No record yet"
            emptyDescription="Your attendance for today appears once your first login is processed."
          >
            {today.data && (
              <div className="flex flex-1 flex-col gap-3">
                <div className="flex items-center justify-between">
                  <Badge tone={STATUS_TONE[today.data.status]} className="px-2.5 py-1 text-xs">
                    {today.data.status.replace('_', ' ')}
                  </Badge>
                  <span className="font-mono text-xs text-muted">
                    First login {formatTime(today.data.firstLoginAt)}
                  </span>
                </div>
                {today.data.isAutoMarked && (
                  <p className="rounded-lg border border-warning/30 bg-warning/10 p-2.5 text-xs leading-relaxed text-warning">
                    Auto-marked by the late rules engine. Open Attendance to see the full warning
                    trail.
                  </p>
                )}
                {rtOpen && (
                  <p className="mt-auto flex items-center gap-1.5 text-xs text-muted">
                    <LiveDot /> Live — status changes appear without refreshing.
                  </p>
                )}
              </div>
            )}
          </WidgetBody>
        </Widget>

        {/* My tasks */}
        <Widget
          title="My tasks"
          icon={ListTodo}
          className="md:col-span-5"
          delay={0.05}
          action={
            <Link
              to="/tasks"
              className="inline-flex items-center gap-1 text-xs font-medium text-primary-ink hover:underline"
            >
              View all <ArrowRight className="h-3 w-3" aria-hidden />
            </Link>
          }
        >
          <WidgetBody
            isLoading={myTasks.isLoading}
            error={myTasks.error}
            onRetry={() => void myTasks.refetch()}
            isEmpty={tasks.length === 0}
            emptyTitle="No tasks yet"
            emptyDescription="Tasks assigned to you appear here with deadlines and status."
          >
            <div className="mb-3 grid grid-cols-4 gap-1.5">
              {(['ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'COMPLETED'] as const).map((s) => (
                <div
                  key={s}
                  className="rounded-lg border border-edge/70 bg-surface-2/60 px-2 py-1.5 text-center"
                >
                  <p className="text-sm font-semibold text-fg">{taskCounts[s]}</p>
                  <p className="mt-0.5 text-[9px] uppercase tracking-wider text-faint">
                    {s.replace('_', ' ')}
                  </p>
                </div>
              ))}
            </div>
            <ul className="space-y-1.5">
              {tasks.slice(0, 5).map((t) => (
                <li key={t.id}>
                  <Link
                    to="/tasks"
                    className="flex items-center gap-2 rounded-lg border border-edge/50 bg-surface-2/40 px-2.5 py-2 transition-colors hover:bg-surface-2"
                  >
                    <span
                      aria-hidden
                      className={cn(
                        'h-1.5 w-1.5 shrink-0 rounded-full',
                        t.overdue
                          ? 'bg-danger'
                          : t.status === 'COMPLETED'
                            ? 'bg-success'
                            : 'bg-primary',
                      )}
                    />
                    <span className="truncate text-xs text-fg">{t.title}</span>
                    {t.overdue && (
                      <Badge tone="danger" className="ml-auto shrink-0">
                        OVERDUE
                      </Badge>
                    )}
                    {!t.overdue && t.lateSubmitted && (
                      <Badge tone="warning" className="ml-auto shrink-0">
                        LATE
                      </Badge>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </WidgetBody>
        </Widget>

        {/* Quick actions — role-aware (§6.2) */}
        <Widget title="Quick actions" icon={Command} className="md:col-span-3" delay={0.1}>
          <div className="flex flex-1 flex-col gap-2">
            <Button
              variant="secondary"
              size="sm"
              className="justify-start"
              onClick={() => setPaletteOpen(true)}
            >
              <Command className="h-3.5 w-3.5" aria-hidden />
              Command palette
            </Button>
            <Button asChild variant="secondary" size="sm" className="justify-start">
              <Link to="/leave">
                <PlaneTakeoff className="h-3.5 w-3.5" aria-hidden />
                Apply for leave
              </Link>
            </Button>
            <Button asChild variant="secondary" size="sm" className="justify-start">
              <Link to={isManager || isPeopleRole ? '/tasks' : '/attendance'}>
                {isManager || isPeopleRole ? (
                  <>
                    <Plus className="h-3.5 w-3.5" aria-hidden />
                    Assign a task
                  </>
                ) : (
                  <>
                    <CalendarCheck className="h-3.5 w-3.5" aria-hidden />
                    My attendance
                  </>
                )}
              </Link>
            </Button>
            <Button asChild variant="secondary" size="sm" className="justify-start">
              <Link to="/tasks">
                <ListTodo className="h-3.5 w-3.5" aria-hidden />
                View my tasks
              </Link>
            </Button>
          </div>
        </Widget>

        {/* Leave balance */}
        <Widget
          title="Leave balance"
          icon={PlaneTakeoff}
          className="md:col-span-4"
          delay={0.15}
          action={
            <Link to="/leave" className="text-xs font-medium text-primary-ink hover:underline">
              Open
            </Link>
          }
        >
          <WidgetBody
            isLoading={balance.isLoading}
            error={balance.error}
            onRetry={() => void balance.refetch()}
            isEmpty={(balance.data ?? []).length === 0}
            emptyTitle="No balance data"
            emptyDescription="Balances appear here as soon as leave management is available."
          >
            <div className="space-y-3">
              {(balance.data ?? []).slice(0, 4).map((b) => {
                const pct = b.total > 0 ? Math.min(100, Math.round((b.used / b.total) * 100)) : 0;
                return (
                  <div key={b.type}>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-fg">{b.type}</span>
                      <span className="font-mono text-muted">
                        {b.remaining}/{b.total} left
                      </span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-3">
                      <div
                        className="h-full rounded-full bg-primary transition-all duration-500 ease-snappy"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </WidgetBody>
        </Widget>

        {/* Organization headcount — ADMIN/HR */}
        {isPeopleRole && (
          <Widget
            title="Organization"
            icon={Building2}
            className="md:col-span-4"
            delay={0.2}
            action={
              <Link
                to="/organization/employees"
                className="text-xs font-medium text-primary-ink hover:underline"
              >
                Directory
              </Link>
            }
          >
            <WidgetBody
              isLoading={headcount.isLoading || departments.isLoading}
              error={headcount.error ?? departments.error}
              onRetry={() => {
                void headcount.refetch();
                void departments.refetch();
              }}
            >
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg border border-edge/70 bg-surface-2/60 p-3">
                  <p className="text-2xl font-semibold tracking-tight text-fg">
                    {headcount.data?.length ?? 0}
                  </p>
                  <p className="mt-0.5 text-xs text-muted">Employees</p>
                </div>
                <div className="rounded-lg border border-edge/70 bg-surface-2/60 p-3">
                  <p className="text-2xl font-semibold tracking-tight text-fg">
                    {departments.data?.length ?? 0}
                  </p>
                  <p className="mt-0.5 text-xs text-muted">Departments</p>
                </div>
              </div>
              <p className="mt-auto pt-3 text-xs text-muted">
                Headcount from identity-service, updated live as records change.
              </p>
            </WidgetBody>
          </Widget>
        )}

        {/* Team overview — MANAGER */}
        {isManager && (
          <Widget title="Team overview" icon={Users} className="md:col-span-4" delay={0.2}>
            <WidgetBody
              isLoading={teamTasks.isLoading}
              error={teamTasks.error}
              onRetry={() => void teamTasks.refetch()}
              isEmpty={(teamTasks.data ?? []).length === 0}
              emptyTitle="No team tasks"
              emptyDescription="Tasks you assign to your department show up here."
            >
              {(() => {
                const team = teamTasks.data ?? [];
                const open = team.filter(
                  (t) => t.status === 'ASSIGNED' || t.status === 'IN_PROGRESS',
                ).length;
                const overdue = team.filter((t) => t.overdue).length;
                const awaiting = team.filter((t) => t.status === 'SUBMITTED').length;
                return (
                  <div className="grid grid-cols-3 gap-3">
                    <div className="rounded-lg border border-edge/70 bg-surface-2/60 p-3 text-center">
                      <p className="text-2xl font-semibold tracking-tight text-fg">{open}</p>
                      <p className="mt-0.5 text-[11px] text-muted">Open</p>
                    </div>
                    <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-center">
                      <p className="text-2xl font-semibold tracking-tight text-danger">{overdue}</p>
                      <p className="mt-0.5 text-[11px] text-muted">Overdue</p>
                    </div>
                    <div className="rounded-lg border border-warning/30 bg-warning/10 p-3 text-center">
                      <p className="text-2xl font-semibold tracking-tight text-warning">{awaiting}</p>
                      <p className="mt-0.5 text-[11px] text-muted">To review</p>
                    </div>
                  </div>
                );
              })()}
            </WidgetBody>
          </Widget>
        )}

        {/* Live activity — genuinely real-time only (§4.1) */}
        <Widget
          title="Live activity"
          icon={Radio}
          className={cn('md:col-span-4', !isPeopleRole && !isManager && 'md:col-span-8')}
          delay={0.25}
          action={<LiveDot />}
        >
          {activity.length === 0 ? (
            <EmptyState
              title="Waiting for events"
              description="Real-time events from attendance, tasks, leave and notifications stream in here the moment they happen."
              className="py-6"
            />
          ) : (
            <ul className="space-y-1.5">
              {activity.map((a) => (
                <li
                  key={a.key}
                  className="flex items-center gap-2 rounded-lg border border-edge/50 bg-surface-2/40 px-2.5 py-1.5"
                >
                  <span aria-hidden className="h-1.5 w-1.5 shrink-0 rounded-full bg-accent" />
                  <Badge tone="neutral" className="shrink-0 font-mono">
                    {a.channel}
                  </Badge>
                  <span className="truncate font-mono text-xs text-muted">{a.event}</span>
                  <span className="ml-auto shrink-0 font-mono text-[10px] text-faint">
                    {formatTime(a.at)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Widget>
      </div>
    </div>
  );
}

