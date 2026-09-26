import { zodResolver } from '@hookform/resolvers/zod';
import { PlaneTakeoff } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAuthStore } from '@/features/auth/authStore';
import { useApplyLeave, useDecideLeave, useLeaveBalance, useLeaveRequests } from '@/shared/api/worklife';
import { useChannel } from '@/shared/realtime/useChannel';
import { useQueryClient } from '@tanstack/react-query';
import { Badge, type BadgeProps } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { DataTable } from '@/shared/ui/DataTable';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { errorMessage } from '@/shared/types/api';
import type { LeaveRequest, LeaveStatus } from '@/shared/types/domain';
import { formatDate } from '@/shared/utils/format';
import type { ColumnDef } from '@tanstack/react-table';

const STATUS_TONE: Record<LeaveStatus, NonNullable<BadgeProps['tone']>> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
};

const leaveSchema = z
  .object({
    type: z.string().min(1, 'Pick a leave type'),
    fromDate: z.string().min(1, 'Pick a start date'),
    toDate: z.string().min(1, 'Pick an end date'),
    reason: z.string().trim().min(5, 'A short reason helps your manager decide'),
  })
  .refine((v) => new Date(v.toDate) >= new Date(v.fromDate), {
    message: 'End date must be on or after the start date',
    path: ['toDate'],
  });

type LeaveForm = z.infer<typeof leaveSchema>;

/** Leave management (§6.5): apply, track, and — for managers — decide. */
export default function LeavePage() {
  const role = useAuthStore((s) => s.claims?.role);
  const canDecide = role === 'MANAGER' || role === 'HR' || role === 'ADMIN';
  const queryClient = useQueryClient();

  const balance = useLeaveBalance();
  const requests = useLeaveRequests();
  const applyLeave = useApplyLeave();
  const decideLeave = useDecideLeave();

  // Live approvals (§6.5 + §5.3): leave events reconcile the cache.
  useChannel('leave', () => {
    void queryClient.invalidateQueries({ queryKey: ['leave'] });
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<LeaveForm>({
    resolver: zodResolver(leaveSchema),
    defaultValues: { type: 'CASUAL', fromDate: '', toDate: '', reason: '' },
  });

  const onApply = async (values: LeaveForm) => {
    try {
      await applyLeave.mutateAsync({
        type: values.type,
        fromDate: new Date(values.fromDate).toISOString(),
        toDate: new Date(values.toDate).toISOString(),
        reason: values.reason,
      });
      toast.success('Leave application submitted.');
      reset();
    } catch (err) {
      toast.error('Application failed', { description: errorMessage(err) });
    }
  };

  const onDecide = async (id: number, decision: 'APPROVED' | 'REJECTED') => {
    try {
      await decideLeave.mutateAsync({ id, decision });
      toast.success(`Leave ${decision.toLowerCase()}.`);
    } catch (err) {
      toast.error('Decision failed', { description: errorMessage(err) });
    }
  };

  const columns: ColumnDef<LeaveRequest>[] = [
    {
      accessorKey: 'type',
      header: 'Type',
      cell: ({ getValue }) => <Badge tone="neutral">{String(getValue<string>())}</Badge>,
    },
    {
      id: 'dates',
      header: 'Dates',
      cell: ({ row }) => (
        <span className="font-mono text-xs">
          {formatDate(row.original.fromDate)} → {formatDate(row.original.toDate)}
        </span>
      ),
    },
    {
      accessorKey: 'reason',
      header: 'Reason',
      cell: ({ getValue }) => (
        <span className="block max-w-xs truncate text-muted">{String(getValue<string>())}</span>
      ),
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ getValue }) => {
        const status = getValue<LeaveStatus>();
        return <Badge tone={STATUS_TONE[status]}>{status}</Badge>;
      },
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canDecide && row.original.status === 'PENDING' ? (
          <span className="flex justify-end gap-1.5">
            <Button size="sm" variant="secondary" onClick={() => onDecide(row.original.id, 'APPROVED')}>
              Approve
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="text-danger hover:text-danger"
              onClick={() => onDecide(row.original.id, 'REJECTED')}
            >
              Reject
            </Button>
          </span>
        ) : null,
    },
  ];

  return (
    <div>
      <PageHeader
        title="Leave"
        description="Apply, track, and approve time off — balances straight from the backend."
      />

      {/* Balances */}
      {balance.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="h-24 animate-pulse bg-surface-2" />
          ))}
        </div>
      ) : balance.error ? (
        <Card plain>
          <CardContent className="pt-5 text-sm text-muted">
            Balances are unavailable right now — the leave service may not be running yet.{' '}
            <button
              type="button"
              className="text-primary-ink underline-offset-2 hover:underline"
              onClick={() => void balance.refetch()}
            >
              Retry
            </button>
          </CardContent>
        </Card>
      ) : (balance.data ?? []).length === 0 ? (
        <Card plain>
          <CardContent className="pt-5 text-sm text-muted">
            No leave balances have been configured for you yet.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {(balance.data ?? []).map((b) => (
            <Card key={b.type} className="p-4">
              <p className="text-xs text-muted">{b.type}</p>
              <p className="mt-1 text-2xl font-semibold tracking-tight text-fg">
                {b.remaining}
                <span className="text-sm font-normal text-faint"> / {b.total}</span>
              </p>
              <p className="mt-1 text-[11px] text-faint">{b.used} used</p>
            </Card>
          ))}
        </div>
      )}

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {/* Apply */}
        <Card>
          <CardHeader>
            <CardTitle>Apply for leave</CardTitle>
            <CardDescription>Your manager is notified the moment you submit.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onApply)} className="space-y-4" noValidate>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="leave-type" className="text-sm font-medium text-fg">
                  Type
                </label>
                <select
                  id="leave-type"
                  className="h-9 w-full rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                  {...register('type')}
                >
                  <option value="CASUAL">Casual</option>
                  <option value="SICK">Sick</option>
                  <option value="PAID">Paid</option>
                  <option value="UNPAID">Unpaid</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="From"
                  type="date"
                  error={errors.fromDate?.message}
                  {...register('fromDate')}
                />
                <Input label="To" type="date" error={errors.toDate?.message} {...register('toDate')} />
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor="leave-reason" className="text-sm font-medium text-fg">
                  Reason
                </label>
                <textarea
                  id="leave-reason"
                  rows={3}
                  className="w-full rounded-lg border border-edge bg-surface-2 px-3 py-2 text-sm text-fg placeholder:text-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                  placeholder="Family function, medical appointment…"
                  {...register('reason')}
                />
                {errors.reason && (
                  <p role="alert" className="text-xs text-danger">
                    {errors.reason.message}
                  </p>
                )}
              </div>
              <Button type="submit" className="w-full" loading={isSubmitting}>
                <PlaneTakeoff className="h-4 w-4" aria-hidden />
                Submit application
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Requests */}
        <div className="lg:col-span-2">
          <DataTable
            columns={columns}
            data={requests.data}
            isLoading={requests.isLoading}
            error={requests.error}
            onRetry={() => void requests.refetch()}
            searchValue={(r) => `${r.type} ${r.status} ${r.reason}`}
            searchPlaceholder="Search requests…"
            emptyTitle="No leave requests"
            emptyDescription={
              canDecide ? 'Requests from your team appear here.' : 'Your applications appear here.'
            }
          />
        </div>
      </div>
    </div>
  );
}
