import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  CheckCircle2,
  ClipboardList,
  Columns3,
  ListChecks,
  Paperclip,
  Plus,
  Send,
} from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { useEmployees } from '@/shared/api/identity';
import { queryKeys } from '@/shared/api/queryKeys';
import {
  useCreateTask,
  useMyTasks,
  useTaskSubmissionUpload,
  useTeamTasks,
  useUpdateTaskStatus,
} from '@/shared/api/worklife';
import { useChannel } from '@/shared/realtime/useChannel';
import { Badge, type BadgeProps } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Dialog, DialogContent } from '@/shared/ui/Dialog';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Skeleton } from '@/shared/ui/Skeleton';
import { errorMessage } from '@/shared/types/api';
import type { Task, TaskPriority, TaskStatus } from '@/shared/types/domain';
import { formatDate } from '@/shared/utils/format';
import { cn } from '@/shared/utils/cn';
import { toast } from 'sonner';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

const STATUS_TONE: Record<TaskStatus, NonNullable<BadgeProps['tone']>> = {
  ASSIGNED: 'neutral',
  IN_PROGRESS: 'info',
  SUBMITTED: 'warning',
  COMPLETED: 'success',
};

const PRIORITY_TONE: Record<TaskPriority, NonNullable<BadgeProps['tone']>> = {
  LOW: 'neutral',
  MEDIUM: 'info',
  HIGH: 'warning',
  URGENT: 'danger',
};

const STATUSES: TaskStatus[] = ['ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'COMPLETED'];

const taskSchema = z.object({
  title: z.string().trim().min(3, 'Title must be at least 3 characters'),
  description: z.string().trim().optional(),
  assigneeId: z.coerce.number().min(1, 'Pick an assignee'),
  deadline: z.string().optional(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
});

type TaskForm = z.infer<typeof taskSchema>;

/**
 * Task management (§6.4) — the major differentiator. Kanban + list views,
 * unambiguous OVERDUE / LATE states, optimistic status changes reconciled
 * against the authoritative event (§8), and assignment UI that reflects the
 * server-side departmentIds constraint exactly.
 */
export default function TasksPage() {
  const claims = useAuthStore((s) => s.claims);
  const role = claims?.role;
  const canAssign = role === 'MANAGER' || role === 'HR' || role === 'ADMIN';
  const isManager = role === 'MANAGER';

  const queryClient = useQueryClient();
  const myTasks = useMyTasks();
  const teamTasks = useTeamTasks(isManager);
  const employees = useEmployees();
  const updateStatus = useUpdateTaskStatus();
  const createTask = useCreateTask();
  const upload = useTaskSubmissionUpload();

  const [view, setView] = useState<'list' | 'board'>('list');
  const [assignOpen, setAssignOpen] = useState(false);

  // Live reconciliation (§5.3): task events on the shared socket invalidate
  // exactly this cache — no polling, no stale boards.
  useChannel('tasks', () => {
    void queryClient.invalidateQueries({ queryKey: ['tasks'] });
  });

  const visibleTasks = isManager ? teamTasks.data ?? [] : myTasks.data ?? [];
  const query = isManager ? teamTasks : myTasks;

  // Optimistic status change with rollback + authoritative reconcile (§8).
  const changeStatus = (task: Task, status: TaskStatus) => {
    const keys = [queryKeys.worklife.myTasks(), queryKeys.worklife.teamTasks()];
    const previous = keys.map((k) => queryClient.getQueryData<Task[]>(k));
    keys.forEach((key) => {
      queryClient.setQueryData<Task[]>(key, (old) =>
        old?.map((t) => (t.id === task.id ? { ...t, status } : t)),
      );
    });
    updateStatus.mutate(
      { id: task.id, status },
      {
        onError: (err) => {
          keys.forEach((key, i) => queryClient.setQueryData(key, previous[i]));
          toast.error('Status update failed', { description: errorMessage(err) });
        },
        onSettled: () => {
          void queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
      },
    );
  };

  const onUpload = async (taskId: number, file: File) => {
    try {
      await upload.mutateAsync({ taskId, file });
      toast.success('File submitted to S3.');
    } catch (err) {
      toast.error('Upload failed', { description: errorMessage(err) });
    }
  };

  // Assignment limited to the manager's departments — same rule the server
  // enforces; the UI never offers assignment outside it (§6.4).
  const assignableEmployees = useMemo(() => {
    const all = employees.data ?? [];
    if (!isManager) return all;
    const deptIds = new Set((claims?.departmentIds ?? []).map(String));
    return all.filter((e) => e.departmentId !== null && deptIds.has(String(e.departmentId)));
  }, [employees.data, isManager, claims]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<TaskForm>({
    resolver: zodResolver(taskSchema),
    defaultValues: { title: '', description: '', assigneeId: 0, deadline: '', priority: 'MEDIUM' },
  });

  const onAssign = async (values: TaskForm) => {
    try {
      await createTask.mutateAsync({
        title: values.title,
        description: values.description || undefined,
        assigneeId: values.assigneeId,
        deadline: values.deadline ? new Date(values.deadline).toISOString() : null,
        priority: values.priority,
      });
      toast.success('Task assigned.');
      reset();
      setAssignOpen(false);
    } catch (err) {
      toast.error('Could not assign task', { description: errorMessage(err) });
    }
  };

  const loading = query.isLoading;
  const error = query.error;
  const retry = () => void query.refetch();

  return (
    <div>
      <PageHeader
        title="Tasks"
        description={
          isManager
            ? 'Your department board — updates arrive live, no refreshing.'
            : 'Everything assigned to you, with deadlines front and center.'
        }
        actions={
          <>
            <div className="flex rounded-lg border border-edge bg-surface-2 p-0.5">
              <button
                type="button"
                onClick={() => setView('list')}
                aria-label="List view"
                aria-pressed={view === 'list'}
                className={cn(
                  'rounded-md px-2.5 py-1.5 transition-colors',
                  view === 'list' ? 'bg-surface-1 text-fg shadow-soft' : 'text-muted hover:text-fg',
                )}
              >
                <ListChecks className="h-4 w-4" aria-hidden />
              </button>
              <button
                type="button"
                onClick={() => setView('board')}
                aria-label="Board view"
                aria-pressed={view === 'board'}
                className={cn(
                  'rounded-md px-2.5 py-1.5 transition-colors',
                  view === 'board' ? 'bg-surface-1 text-fg shadow-soft' : 'text-muted hover:text-fg',
                )}
              >
                <Columns3 className="h-4 w-4" aria-hidden />
              </button>
            </div>
            {canAssign && (
              <Button size="sm" onClick={() => setAssignOpen(true)}>
                <Plus className="h-4 w-4" aria-hidden />
                Assign task
              </Button>
            )}
          </>
        }
      />

      {loading ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-36 rounded-card" />
          ))}
        </div>
      ) : error ? (
        <ErrorState error={error} onRetry={retry} title="Couldn't load tasks" />
      ) : visibleTasks.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title={isManager ? 'No tasks in your department' : 'No tasks yet'}
          description={
            isManager
              ? 'Assign your first task to get the board moving.'
              : 'Tasks assigned to you will appear here.'
          }
          action={
            canAssign ? (
              <Button size="sm" onClick={() => setAssignOpen(true)}>
                <Plus className="h-4 w-4" aria-hidden />
                Assign task
              </Button>
            ) : undefined
          }
        />
      ) : view === 'list' ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {visibleTasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              onStatus={changeStatus}
              onUpload={(file) => onUpload(task.id, file)}
              uploading={upload.isPending && upload.variables?.taskId === task.id}
            />
          ))}
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {STATUSES.map((status) => {
            const columnTasks = visibleTasks.filter((t) => t.status === status);
            return (
              <div key={status} className="flex min-w-0 flex-col gap-3">
                <div className="flex items-center justify-between px-1">
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">
                    {status.replace('_', ' ')}
                  </h3>
                  <Badge tone="neutral">{columnTasks.length}</Badge>
                </div>
                {columnTasks.length === 0 ? (
                  <div className="rounded-card border border-dashed border-edge px-3 py-6 text-center text-xs text-faint">
                    Nothing here
                  </div>
                ) : (
                  columnTasks.map((task) => (
                    <TaskCard
                      key={task.id}
                      task={task}
                      onStatus={changeStatus}
                      onUpload={(file) => onUpload(task.id, file)}
                      uploading={upload.isPending && upload.variables?.taskId === task.id}
                    />
                  ))
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Assign task — assignee list mirrors the server-side constraint (§6.4) */}
      <Dialog open={assignOpen} onOpenChange={setAssignOpen}>
        <DialogContent
          title="Assign a task"
          description="Deadlines and priority are visible to the assignee immediately."
        >
          <form onSubmit={handleSubmit(onAssign)} className="space-y-4" noValidate>
            <Input
              label="Title"
              placeholder="Prepare Q3 compliance report"
              error={errors.title?.message}
              {...register('title')}
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="task-desc" className="text-sm font-medium text-fg">
                Description <span className="text-faint">(optional)</span>
              </label>
              <textarea
                id="task-desc"
                rows={3}
                className="w-full rounded-lg border border-edge bg-surface-2 px-3 py-2 text-sm text-fg placeholder:text-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                placeholder="Context, acceptance criteria, links…"
                {...register('description')}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="task-assignee" className="text-sm font-medium text-fg">
                Assignee
              </label>
              <select
                id="task-assignee"
                className="h-9 w-full rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                {...register('assigneeId')}
              >
                <option value={0}>Select an employee…</option>
                {assignableEmployees.map((e) => (
                  <option key={e.id} value={e.id}>
                    {e.name}
                  </option>
                ))}
              </select>
              {errors.assigneeId && (
                <p role="alert" className="text-xs text-danger">
                  {errors.assigneeId.message}
                </p>
              )}
              {isManager && (
                <p className="text-xs text-faint">
                  Limited to your departments ({(claims?.departmentIds ?? []).join(', ') || 'none'}) —
                  exactly what the server allows.
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Deadline"
                type="date"
                error={errors.deadline?.message}
                {...register('deadline')}
              />
              <div className="flex flex-col gap-1.5">
                <label htmlFor="task-priority" className="text-sm font-medium text-fg">
                  Priority
                </label>
                <select
                  id="task-priority"
                  className="h-9 w-full rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                  {...register('priority')}
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="URGENT">Urgent</option>
                </select>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setAssignOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>
                Assign
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Single task card — OVERDUE/LATE states are unambiguous, not just a color (§6.4). */
function TaskCard({
  task,
  onStatus,
  onUpload,
  uploading,
}: {
  task: Task;
  onStatus: (task: Task, status: TaskStatus) => void;
  onUpload: (file: File) => void;
  uploading: boolean;
}) {
  const next: TaskStatus | null =
    task.status === 'ASSIGNED'
      ? 'IN_PROGRESS'
      : task.status === 'IN_PROGRESS'
        ? 'SUBMITTED'
        : task.status === 'SUBMITTED'
          ? 'COMPLETED'
          : null;
  const nextLabel =
    next === 'IN_PROGRESS' ? 'Start' : next === 'SUBMITTED' ? 'Submit' : 'Mark complete';
  const inputId = `task-upload-${task.id}`;

  return (
    <Card className="flex flex-col p-3.5">
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium leading-snug text-fg">{task.title}</p>
        <Badge tone={PRIORITY_TONE[task.priority]} className="shrink-0">
          {task.priority}
        </Badge>
      </div>

      {task.description && (
        <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-muted">{task.description}</p>
      )}

      <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
        <Badge tone={STATUS_TONE[task.status]}>{task.status.replace('_', ' ')}</Badge>
        {task.overdue && <Badge tone="danger">OVERDUE</Badge>}
        {task.lateSubmitted && <Badge tone="warning">LATE_SUBMITTED</Badge>}
        {task.deadline && (
          <span
            className={cn('font-mono text-[10px]', task.overdue ? 'text-danger' : 'text-faint')}
          >
            Due {formatDate(task.deadline)}
          </span>
        )}
      </div>

      <div className="mt-3 flex items-center gap-1.5 border-t border-edge/50 pt-2.5">
        {next && (
          <Button size="sm" variant="secondary" onClick={() => onStatus(task, next)}>
            {next === 'COMPLETED' ? (
              <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
            ) : next === 'SUBMITTED' ? (
              <Send className="h-3.5 w-3.5" aria-hidden />
            ) : undefined}
            {nextLabel}
          </Button>
        )}
        <label
          htmlFor={inputId}
          title="Upload submission (goes directly to S3 via pre-signed URL)"
          className={cn(
            'ml-auto flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-fg',
            uploading && 'animate-pulse text-primary-ink',
          )}
        >
          <Paperclip className="h-3.5 w-3.5" aria-hidden />
          <input
            id={inputId}
            type="file"
            className="sr-only"
            disabled={uploading}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onUpload(file);
              e.target.value = '';
            }}
          />
        </label>
      </div>
    </Card>
  );
}


