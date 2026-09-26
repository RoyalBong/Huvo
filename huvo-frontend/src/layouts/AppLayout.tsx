import { useState, type ReactNode } from 'react';
import { Drawer } from 'vaul';
import { CommandPalette } from '@/shared/command-palette/CommandPalette';
import { Logo } from '@/shared/ui/Logo';
import { Header } from '@/layouts/Header';
import { NavLinks, Sidebar, UserChip } from '@/layouts/Sidebar';

/**
 * Authenticated app shell: fixed sidebar (desktop), glass header, and a
 * bottom-sheet-driven mobile nav (§6.10 — not a hamburger dumping the full
 * desktop menu onto a small screen).
 */
export function AppLayout({ children }: { children: ReactNode }) {
  const [navOpen, setNavOpen] = useState(false);

  return (
    <div className="min-h-dvh">
      <Sidebar />

      <div className="lg:pl-60">
        <Header onOpenNav={() => setNavOpen(true)} />
        <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </main>
      </div>

      {/* Mobile navigation — Vaul bottom sheet (§3) */}
      <Drawer.Root open={navOpen} onOpenChange={setNavOpen}>
        <Drawer.Portal>
          <Drawer.Overlay className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm" />
          <Drawer.Content
            className="fixed inset-x-0 bottom-0 z-50 flex max-h-[78vh] flex-col rounded-t-2xl border-t border-edge bg-surface-1 shadow-lift focus:outline-none"
            aria-label="Navigation"
          >
            <Drawer.Title className="sr-only">Navigation</Drawer.Title>
            <div className="mx-auto mt-3 h-1 w-10 shrink-0 rounded-full bg-edge" aria-hidden />
            <div className="flex h-12 shrink-0 items-center border-b border-edge/60 px-4">
              <Logo />
            </div>
            <NavLinks onNavigate={() => setNavOpen(false)} />
            <UserChip />
          </Drawer.Content>
        </Drawer.Portal>
      </Drawer.Root>

      <CommandPalette />
    </div>
  );
}
