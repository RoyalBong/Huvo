import { RefreshCw } from 'lucide-react';
import { cn } from '@/shared/utils/cn';
import { useRealtimeStore } from '@/shared/realtime/socket';

/**
 * Small, honest connection feedback (§5.3): a compact "reconnecting…" pill
 * whenever the socket isn't live — never silently pretend stale data is live.
 * When live, callers use <LiveDot /> for the §4.1 live-state language.
 */
export function ReconnectBanner({ className }: { className?: string }) {
  const status = useRealtimeStore((s) => s.status);

  if (status === 'open' || status === 'idle') return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex items-center gap-1.5 rounded-full border border-warning/30 bg-warning/10 px-2.5 py-1 text-xs font-medium text-warning',
        className,
      )}
    >
      <RefreshCw className="h-3 w-3 animate-spin" aria-hidden />
      {status === 'connecting' ? 'Connecting…' : 'Reconnecting…'}
    </div>
  );
}

/**
 * The live-state color language (§4.1): a small animated dot — used ONLY for
 * things that are genuinely real-time, so the vocabulary stays meaningful.
 */
export function LiveDot({ className }: { className?: string }) {
  const status = useRealtimeStore((s) => s.status);
  if (status !== 'open') return null;

  return (
    <span
      aria-label="Live"
      className={cn('relative inline-flex h-2 w-2', className)}
      title="Live"
    >
      <span className="absolute inline-flex h-full w-full animate-live-pulse rounded-full bg-accent" />
      <span className="relative inline-flex h-2 w-2 rounded-full bg-accent" />
    </span>
  );
}
