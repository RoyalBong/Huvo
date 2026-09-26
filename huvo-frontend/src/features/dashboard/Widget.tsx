import type { ReactNode } from 'react';
import { motion } from 'framer-motion';
import type { LucideIcon } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { ErrorState, EmptyState } from '@/shared/ui/States';
import { Skeleton } from '@/shared/ui/Skeleton';
import { cn } from '@/shared/utils/cn';

/**
 * Bento dashboard widget (§4.4 — bento grids are exactly right for the
 * role-aware summary widgets) with first-class loading/empty/error states (§9).
 * Staggered reveal happens on first mount only (§4.3).
 */
export function Widget({
  title,
  description,
  icon: Icon,
  action,
  className,
  delay = 0,
  children,
}: {
  title: string;
  description?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  className?: string;
  delay?: number;
  children: ReactNode;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay, ease: [0.22, 1, 0.36, 1] }}
      className={cn('min-w-0', className)}
    >
      <Card className="flex h-full flex-col">
        <CardHeader className="flex-row items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            {Icon && <Icon className="h-4 w-4 text-primary-ink" aria-hidden />}
            <div>
              <CardTitle>{title}</CardTitle>
              {description && <CardDescription className="mt-0.5">{description}</CardDescription>}
            </div>
          </div>
          {action}
        </CardHeader>
        <div className="flex flex-1 flex-col px-5 pb-5">{children}</div>
      </Card>
    </motion.div>
  );
}

/** Renders the right state for a widget backed by a query. */
export function WidgetBody({
  isLoading,
  error,
  onRetry,
  isEmpty = false,
  emptyTitle = 'Nothing here yet',
  emptyDescription,
  emptyIcon,
  children,
}: {
  isLoading: boolean;
  error: unknown;
  onRetry?: () => void;
  isEmpty?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyIcon?: LucideIcon;
  children: ReactNode;
}) {
  if (isLoading) {
    return (
      <div className="space-y-2.5" aria-busy>
        <Skeleton className="h-5 w-2/5" />
        <Skeleton className="h-4 w-4/5" />
        <Skeleton className="h-4 w-3/5" />
      </div>
    );
  }
  if (error) {
    return <ErrorState error={error} onRetry={onRetry} className="py-6" />;
  }
  if (isEmpty) {
    return <EmptyState title={emptyTitle} description={emptyDescription} icon={emptyIcon} className="py-6" />;
  }
  return <>{children}</>;
}
