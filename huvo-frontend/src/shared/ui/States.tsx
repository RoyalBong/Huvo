import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { AlertTriangle, Inbox, RotateCw } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { errorMessage, isApiError } from '@/shared/types/api';
import { cn } from '@/shared/utils/cn';

/**
 * Empty and error states are first-class UI (§9) — the app has no mock data
 * to lean on, so these have to look good on their own and stay honest.
 */

interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ icon: Icon = Inbox, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-2 px-6 py-14 text-center', className)}>
      <div className="mb-1 flex h-11 w-11 items-center justify-center rounded-full border border-edge bg-surface-2">
        <Icon className="h-5 w-5 text-muted" aria-hidden />
      </div>
      <p className="text-sm font-semibold text-fg">{title}</p>
      {description && <p className="max-w-sm text-xs leading-relaxed text-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
  title?: string;
  className?: string;
}

export function ErrorState({ error, onRetry, title = "Couldn't load this", className }: ErrorStateProps) {
  const detail = errorMessage(error);
  const isNetwork = isApiError(error) && error.isNetwork;

  return (
    <div
      role="alert"
      className={cn('flex flex-col items-center justify-center gap-2 px-6 py-12 text-center', className)}
    >
      <div className="mb-1 flex h-11 w-11 items-center justify-center rounded-full border border-danger/30 bg-danger/10">
        <AlertTriangle className="h-5 w-5 text-danger" aria-hidden />
      </div>
      <p className="text-sm font-semibold text-fg">{title}</p>
      <p className="max-w-md text-xs leading-relaxed text-muted">{detail}</p>
      {isNetwork && (
        <p className="font-mono text-[11px] text-faint">
          The backend may not be running or is unreachable from this environment.
        </p>
      )}
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-2" onClick={onRetry}>
          <RotateCw className="h-3.5 w-3.5" aria-hidden />
          Retry
        </Button>
      )}
    </div>
  );
}
