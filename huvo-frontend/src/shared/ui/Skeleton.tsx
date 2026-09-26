import { Loader2 } from 'lucide-react';
import { cn } from '@/shared/utils/cn';

/** Skeleton loader matching the eventual layout — never a generic gray box far
 *  off from real content (§9). */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('animate-pulse rounded-md bg-surface-3', className)} {...props} />;
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-4 w-4 animate-spin text-muted', className)} aria-label="Loading" />;
}

/** Skeleton rows shaped like the table/list they'll become. */
export function SkeletonRows({ rows = 5, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn('space-y-3 p-4', className)} aria-hidden>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex items-center gap-4">
          <Skeleton className="h-8 w-8 shrink-0 rounded-full" />
          <Skeleton className="h-4 flex-1" style={{ maxWidth: `${45 + ((i * 13) % 40)}%` }} />
          <Skeleton className="ml-auto h-4 w-20" />
        </div>
      ))}
    </div>
  );
}
