import { LogOut } from 'lucide-react';
import { NavLink, useLocation } from 'react-router-dom';
import { navItemsForRole } from '@/app/navigation';
import { useAuthStore } from '@/features/auth/authStore';
import { useEmployee } from '@/shared/api/identity';
import { Logo } from '@/shared/ui/Logo';
import { Avatar } from '@/shared/ui/Avatar';
import { Badge } from '@/shared/ui/Badge';
import { cn } from '@/shared/utils/cn';

/** Shared nav link list — used by the desktop sidebar and the mobile sheet. */
export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const role = useAuthStore((s) => s.claims?.role);
  const location = useLocation();
  const items = navItemsForRole(role);

  return (
    <nav className="flex-1 space-y-0.5 overflow-y-auto p-2" aria-label="Main">
      {items.map((item) => {
        const Icon = item.icon;
        const active =
          item.to === '/'
            ? location.pathname === '/'
            : location.pathname === item.to || location.pathname.startsWith(`${item.to}/`);
        return (
          <NavLink
            key={item.id}
            to={item.to}
            onClick={onNavigate}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors duration-150',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60',
              active
                ? 'bg-surface-2 text-fg shadow-soft'
                : 'text-muted hover:bg-surface-2/60 hover:text-fg',
            )}
          >
            <Icon className={cn('h-4 w-4 shrink-0', active ? 'text-primary-ink' : '')} aria-hidden />
            <span className="truncate font-medium">{item.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
}

/** Identity chip — real name from identity-service, JWT-derived fallback. */
export function UserChip() {
  const claims = useAuthStore((s) => s.claims);
  const logout = useAuthStore((s) => s.logout);
  const { data: employee } = useEmployee(claims?.employeeId ?? null);

  const name = employee?.name ?? (claims ? `Employee #${claims.employeeId}` : '…');

  return (
    <div className="flex items-center gap-2.5 border-t border-edge/60 p-3">
      <Avatar name={name} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-medium text-fg">{name}</p>
        <Badge tone="primary" className="mt-0.5">
          {claims?.role ?? '—'}
        </Badge>
      </div>
      <button
        type="button"
        onClick={() => void logout()}
        aria-label="Sign out"
        title="Sign out"
        className="rounded-md p-1.5 text-muted transition-colors hover:bg-surface-2 hover:text-danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
      >
        <LogOut className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}

/** Desktop sidebar — fixed, quiet, glass-adjacent restraint (§2). */
export function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-edge bg-surface-1/85 backdrop-blur-xl lg:flex">
      <div className="flex h-14 items-center border-b border-edge/60 px-4">
        <Logo />
      </div>
      <NavLinks />
      <UserChip />
    </aside>
  );
}
