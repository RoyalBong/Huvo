import type { ReactNode } from 'react';
import { Logo } from '@/shared/ui/Logo';

/**
 * Auth shell (§6.1): elegant, minimal, first-impression-critical.
 * Brand panel on the left (desktop), centered form card on the right.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh bg-bg">
      {/* Brand panel — calm, no marketing-site gimmicks (§2) */}
      <aside className="relative hidden w-[46%] overflow-hidden border-r border-edge bg-surface-1 lg:block">
        <div
          aria-hidden
          className="absolute -left-32 top-1/4 h-96 w-96 rounded-full bg-primary/20 blur-[120px]"
        />
        <div
          aria-hidden
          className="absolute -right-24 bottom-0 h-80 w-80 rounded-full bg-accent/10 blur-[110px]"
        />
        <div className="relative flex h-full flex-col justify-between p-10">
          <Logo />
          <div className="max-w-sm">
            <h1 className="text-3xl font-semibold leading-tight tracking-tight text-fg">
              The calm, precise way to run people operations.
            </h1>
            <p className="mt-4 text-sm leading-relaxed text-muted">
              Attendance, tasks, leave, payroll and your whole organization — in one
              focused workspace built for growing teams.
            </p>
          </div>
          <p className="font-mono text-xs text-faint">Huvo — HR &amp; Work Management</p>
        </div>
      </aside>

      {/* Form side */}
      <main className="flex flex-1 items-center justify-center px-6 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}
