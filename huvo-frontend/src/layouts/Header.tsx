import { Command, Menu, Moon, Sun } from 'lucide-react';
import { useCommandPalette } from '@/shared/command-palette/registry';
import { useTheme } from '@/shared/hooks/useTheme';
import { LiveDot, ReconnectBanner } from '@/shared/realtime/ReconnectBanner';
import { NotificationsMenu } from '@/layouts/NotificationsMenu';
import { cn } from '@/shared/utils/cn';

/**
 * App header — one of the few glass surfaces (§2). Carries the command
 * palette trigger (§6.11), the honest realtime status (§5.3), notifications,
 * and the dark/light toggle.
 */
export function Header({ onOpenNav }: { onOpenNav: () => void }) {
  const theme = useTheme((s) => s.theme);
  const toggleTheme = useTheme((s) => s.toggle);
  const setPaletteOpen = useCommandPalette((s) => s.setOpen);

  return (
    <header className="glass sticky top-0 z-20 flex h-14 items-center gap-2 px-4 sm:px-6">
      {/* Mobile: menu → bottom sheet (§6.10) */}
      <button
        type="button"
        onClick={onOpenNav}
        aria-label="Open navigation"
        className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60 lg:hidden"
      >
        <Menu className="h-4 w-4" aria-hidden />
      </button>

      <ReconnectBanner className="ml-1" />

      <div className="ml-auto flex items-center gap-1.5">
        {/* Command palette trigger */}
        <button
          type="button"
          onClick={() => setPaletteOpen(true)}
          className={cn(
            'hidden h-8 items-center gap-2 rounded-lg border border-edge bg-surface-2/80 px-2.5',
            'text-xs text-faint transition-colors hover:text-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60',
            'sm:flex',
          )}
          aria-label="Open command palette"
        >
          <Command className="h-3.5 w-3.5" aria-hidden />
          Search or jump to…
          <kbd className="rounded border border-edge bg-surface-1 px-1 py-px font-mono text-[10px]">
            {navigator.platform.toLowerCase().includes('mac') ? '⌘' : 'Ctrl'} K
          </kbd>
        </button>

        <LiveDot className="mx-1.5" />
        <NotificationsMenu />

        <button
          type="button"
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
        >
          {theme === 'dark' ? <Sun className="h-4 w-4" aria-hidden /> : <Moon className="h-4 w-4" aria-hidden />}
        </button>
      </div>
    </header>
  );
}
