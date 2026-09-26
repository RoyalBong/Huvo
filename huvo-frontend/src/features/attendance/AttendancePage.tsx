import { useMemo } from 'react';
import { addDays, format, isToday } from 'date-fns';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuthStore } from '@/features/auth/authStore';
import { useAttendanceDashboard, useAttendanceHistory, useLateTracker, useTodayAttendance } from '@/shared/api/attendance';
import { Badge, type BadgeProps } from '@/shared/ui/Badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { DataTable } from '@/shared/ui/DataTable';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { Skeleton } from '@/shared/ui/Skeleton';
import { formatDate, formatTime } from '@/shared/utils/format';
import { cn } from '@/shared/utils/cn';
import type { AttendanceDashboardRow } from '@/shared/api/attendance';
import type { AttendanceStatus } from '@/shared/types/domain';
import type { ColumnDef } from '@tanstack/react-table';

const STATUS_TONE: Record<AttendanceStatus, NonNullable<BadgeProps['tone']>> = {
  PRESENT: 'success',
  LATE: 'warning',
  ABSENT: 'danger',
  ON_LEAVE: 'info',
  HOLIDAY: 'neutral',
};

const STATUS_CELL: Record<AttendanceStatus, string> = {
  PRESENT: 'bg-success/15 text-success border-success/30',
  LATE: 'bg-warning/15 text-warning border-warning/30',
  ABSENT: 'bg-danger/15 text-danger border-danger/30',
  ON_LEAVE: 'bg-info/15 text-info border-info/30',
  HOLIDAY: 'bg-surface-2 text-muted border-edge',
};

/**
 * Attendance (§6.3): status is always computed by the backend engine — this
 * page only displays it, including the full late/auto-absent warning trail
 * (transparency matters — an employee must see exactly WHY they were marked
 * absent). Managers get the real-time department-grouped dashboard.
 */
export default function AttendancePage() {
  const role = useAuthStore((s) => s.claims?.role);
  const isManagement = role === 'ADMIN' || role === 'HR' || role === 'MANAGER';

  const today = useTodayAttendance();
  const history = useAttendanceHistory();
  const lateTracker = useLateTracker();
  const dashboard = useAttendanceDashboard();

  // Last 7 calendar days for the strip — labels are display scaffold; every
  // status shown comes from the API, never hardcoded (§7).
  const window7 = useMemo(() => {
    const days: { key: string; label: string; status: AttendanceStatus | null; isToday: boolean }[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = addDays(new Date(), -i);
      const key = format(d, 'yyyy-MM-dd');
      const record = (history.data ?? []).find((r) => r.date === key || r.date.startsWith(key));
      days.push({
        key,
        label: format(d, 'EEE'),
        status: record?.status ?? null,
        isToday: isToday(d),
      });
    }
    return days;
  }, [history.data]);

  const statusCounts = useMemo(() => {
    const counts: Record<AttendanceStatus, number> = {
      PRESENT: 0,
      LATE: 0,
      ABSENT: 0,
      ON_LEAVE: 0,
      HOLIDAY: 0,
    };
    for (const row of dashboard.data ?? []) counts[row.status] += 1;
    return Object.entries(counts).map(([status, count]) => ({
      status: status.replace('_', ' '),
      count,
    }));
  }, [dashboard.data]);

  const dashColumns: ColumnDef<AttendanceDashboardRow>[] = [
    {
      accessorKey: 'employeeName',
      header: 'Employee',
      cell: ({ row }) => <span className="font-medium">{row.original.employeeName}</span>,
    },
    {
      accessorKey: 'departmentId',
      header: 'Dept',
      cell: ({ getValue }) => <span className="font-mono text-xs">#{String(getValue<number>())}</span>,
    },
    {
      accessorKey: 'date',
      header: 'Date',
      cell: ({ getValue }) => <span className="font-mono text-xs">{String(getValue<string>())}</span>,
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ getValue }) => {
        const status = getValue<AttendanceStatus>();
        return <Badge tone={STATUS_TONE[status]}>{status.replace('_', ' ')}</Badge>;
      },
    },
    {
      accessorKey: 'firstLoginAt',
      header: 'First login',
      cell: ({ getValue }) => (
        <span className="font-mono text-xs text-muted">{formatTime(getValue<string | null>())}</span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Attendance"
        description="Status is computed by the backend engine — this page shows it, exactly as recorded."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Today */}
        <Card plain className="lg:col-span-1">
          <CardHeader>
            <CardTitle>Today</CardTitle>
            <CardDescription>{formatDate(new Date().toISOString())}</CardDescription>
          </CardHeader>
          <CardContent>
            {today.isLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-6 w-28" />
                <Skeleton className="h-4 w-40" />
              </div>
            ) : today.error ? (
              <ErrorState error={today.error} onRetry={() => void today.refetch()} className="py-4" />
            ) : !today.data ? (
              <EmptyState
                title="No record yet"
                description="Today's record appears once your first login is processed."
                className="py-4"
              />
            ) : (
              <div className="space-y-3">
                <Badge tone={STATUS_TONE[today.data.status]} className="px-3 py-1 text-sm">
                  {today.data.status.replace('_', ' ')}
                </Badge>
                <dl className="space-y-1.5 text-xs">
                  <div className="flex justify-between">
                    <dt className="text-muted">First login</dt>
                    <dd className="font-mono text-fg">{formatTime(today.data.firstLoginAt)}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-muted">Source</dt>
                    <dd className="font-mono text-fg">
                      {today.data.isAutoMarked ? 'Auto-marked' : 'Engine'}
                    </dd>
                  </div>
                  {today.data.reasonNote && (
                    <div className="flex justify-between gap-4">
                      <dt className="shrink-0 text-muted">Note</dt>
                      <dd className="text-right text-fg">{today.data.reasonNote}</dd>
                    </div>
                  )}
                </dl>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Last 7 days strip */}
        <Card plain className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Last 7 days</CardTitle>
            <CardDescription>Your recent attendance at a glance.</CardDescription>
          </CardHeader>
          <CardContent>
            {history.isLoading ? (
              <div className="grid grid-cols-7 gap-2">
                {Array.from({ length: 7 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 rounded-lg" />
                ))}
              </div>
            ) : history.error ? (
              <ErrorState error={history.error} onRetry={() => void history.refetch()} className="py-4" />
            ) : (
              <div className="grid grid-cols-7 gap-2">
                {window7.map((day) => (
                  <div
                    key={day.key}
                    className={cn(
                      'flex flex-col items-center gap-1 rounded-lg border px-1 py-2',
                      day.status
                        ? STATUS_CELL[day.status]
                        : 'border-dashed border-edge bg-surface-2/40 text-faint',
                      day.isToday && 'ring-2 ring-primary/60',
                    )}
                    title={day.status ?? 'No record'}
                  >
                    <span className="text-[10px] font-medium uppercase">{day.label}</span>
                    <span className="text-center text-[10px] font-semibold leading-tight">
                      {day.status ? day.status.replace('_', ' ') : '—'}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Late / auto-absent transparency trail (§6.3) */}
      <Card plain className="mt-4">
        <CardHeader>
          <CardTitle>Late-login history</CardTitle>
          <CardDescription>
            The full warning trail behind late marks and auto-absence — nothing hidden.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {lateTracker.isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-5/6" />
            </div>
          ) : lateTracker.error ? (
            <ErrorState
              error={lateTracker.error}
              onRetry={() => void lateTracker.refetch()}
              className="py-4"
            />
          ) : (lateTracker.data ?? []).length === 0 ? (
            <EmptyState
              title="No late logins recorded"
              description="You're all caught up — nothing has triggered the warning rules."
              className="py-4"
            />
          ) : (
            <ul className="divide-y divide-edge/50">
              {(lateTracker.data ?? []).map((t) => (
                <li
                  key={`${t.employeeId}-${t.weekStartDate}`}
                  className="flex flex-wrap items-center gap-3 py-2.5 text-sm"
                >
                  <span className="font-mono text-xs text-muted">
                    Week of {formatDate(t.weekStartDate)}
                  </span>
                  <Badge
                    tone={
                      t.lateDaysCount >= 2 ? 'danger' : t.lateDaysCount > 0 ? 'warning' : 'success'
                    }
                  >
                    {t.lateDaysCount} late this week
                  </Badge>
                  <Badge tone={t.consecutiveLateDaysCount >= 3 ? 'danger' : 'neutral'}>
                    {t.consecutiveLateDaysCount} consecutive
                  </Badge>
                  <span className="ml-auto text-xs text-faint">
                    Auto-absent triggers at 3 consecutive or 2 in a week
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {/* Manager dashboard */}
      {isManagement && (
        <Card plain className="mt-4">
          <CardHeader>
            <CardTitle>Team attendance</CardTitle>
            <CardDescription>Department-grouped, refreshed by live events.</CardDescription>
          </CardHeader>
          <CardContent className="pt-0">
            {!dashboard.isLoading && !dashboard.error && statusCounts.some((s) => s.count > 0) && (
              <div className="mb-4 h-44 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={statusCounts} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--c-border))" vertical={false} />
                    <XAxis
                      dataKey="status"
                      tick={{ fill: 'rgb(var(--c-muted))', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      allowDecimals={false}
                      tick={{ fill: 'rgb(var(--c-muted))', fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      cursor={{ fill: 'rgb(var(--c-surface-2) / 0.6)' }}
                      contentStyle={{
                        background: 'rgb(var(--c-surface-1))',
                        border: '1px solid rgb(var(--c-border))',
                        borderRadius: 8,
                        fontSize: 12,
                      }}
                    />
                    <Bar dataKey="count" fill="rgb(var(--c-primary))" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}

            <DataTable
              columns={dashColumns}
              data={dashboard.data}
              isLoading={dashboard.isLoading}
              error={dashboard.error}
              onRetry={() => void dashboard.refetch()}
              searchValue={(r) => `${r.employeeName} ${r.status} ${r.date}`}
              searchPlaceholder="Filter by employee or status…"
              emptyTitle="No attendance records"
              emptyDescription="Records appear here as logins are processed by the engine."
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}

