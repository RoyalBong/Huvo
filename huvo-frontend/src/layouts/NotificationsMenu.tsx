import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { Bell, Inbox } from 'lucide-react';
import { useMarkNotificationRead, useNotifications } from '@/shared/api/notify';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { Spinner } from '@/shared/ui/Skeleton';
import { formatRelative } from '@/shared/utils/format';

/** Global notifications — in-app feed in the header (§6.10). */
export function NotificationsMenu() {
  const { data, isLoading, error, refetch } = useNotifications();
  const markRead = useMarkNotificationRead();
  const unread = (data ?? []).filter((n) => !n.readAt);

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          aria-label={`Notifications${unread.length ? ` (${unread.length} unread)` : ''}`}
          className="relative flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
        >
          <Bell className="h-4 w-4" aria-hidden />
          {unread.length > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[9px] font-semibold text-primary-fg">
              {unread.length > 9 ? '9+' : unread.length}
            </span>
          )}
        </button>
      </DropdownMenu.Trigger>

      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="z-50 w-80 overflow-hidden rounded-card border border-edge bg-surface-1 shadow-lift animate-fade-up"
        >
          <div className="border-b border-edge/60 px-4 py-3">
            <DropdownMenu.Label className="text-sm font-semibold text-fg">
              Notifications
            </DropdownMenu.Label>
          </div>

          <div className="max-h-80 overflow-y-auto">
            {isLoading ? (
              <div className="flex items-center justify-center py-8">
                <Spinner />
              </div>
            ) : error ? (
              <ErrorState error={error} onRetry={() => void refetch()} title="Couldn't load notifications" />
            ) : !data || data.length === 0 ? (
              <EmptyState
                icon={Inbox}
                title="You're all clear"
                description="New notifications will appear here as they arrive."
                className="py-8"
              />
            ) : (
              <ul className="divide-y divide-edge/50">
                {data.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => markRead.mutate(n.id)}
                      className="w-full px-4 py-3 text-left transition-colors hover:bg-surface-2/70 focus-visible:outline-none"
                    >
                      <div className="flex items-start gap-2">
                        {!n.readAt && <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />}
                        <div className={n.readAt ? 'pl-3.5' : ''}>
                          <p className="text-xs font-medium text-fg">{n.title}</p>
                          <p className="mt-0.5 text-xs leading-relaxed text-muted">{n.body}</p>
                          <p className="mt-1 font-mono text-[10px] text-faint">{formatRelative(n.createdAt)}</p>
                        </div>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
