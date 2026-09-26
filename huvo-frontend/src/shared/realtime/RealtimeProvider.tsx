import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/features/auth/authStore';
import { huvoSocket } from '@/shared/realtime/socket';

/**
 * Owns the app's single WebSocket lifecycle (§5.3): connects once the user is
 * authenticated, tears down on logout/refresh-token loss.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status);
  const token = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (status === 'authenticated' && token && import.meta.env.VITE_WS_URL) {
      huvoSocket.connect(import.meta.env.VITE_WS_URL, token);
      return () => huvoSocket.disconnect();
    }
    huvoSocket.disconnect();
    return undefined;
  }, [status, token]);

  return <>{children}</>;
}
