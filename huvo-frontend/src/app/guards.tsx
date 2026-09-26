import type { ReactNode } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/States';
import type { Role } from '@/shared/types/auth';

/** Redirects to /login when anonymous — role logic comes only from the JWT (§5.4). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();

  if (status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  }
  return <>{children}</>;
}

/** Login/register only for signed-out users. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status);
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <>{children}</>;
}

/** Route-level Access Role guard — mirrors backend authorization exactly (§5.4). */
export function RequireRoles({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const claims = useAuthStore((s) => s.claims);
  const navigate = useNavigate();

  if (!claims || !roles.includes(claims.role)) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="You don't have access to this module"
        description={`This area is available to: ${roles.join(', ')}. Your access role is ${claims?.role ?? 'unknown'}.`}
        action={<Button variant="secondary" onClick={() => navigate('/')}>Back to dashboard</Button>}
      />
    );
  }
  return <>{children}</>;
}
