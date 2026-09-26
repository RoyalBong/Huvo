import { useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'framer-motion';
import { Toaster, toast } from 'sonner';
import { AppRoutes } from '@/app/router';
import { useAuthStore } from '@/features/auth/authStore';
import { useTheme } from '@/shared/hooks/useTheme';
import { RealtimeProvider } from '@/shared/realtime/RealtimeProvider';
import { Logo } from '@/shared/ui/Logo';
import { Spinner } from '@/shared/ui/Skeleton';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Fail honestly and quickly — no endless spinning against a service
      // that isn't up yet (§7: honest error UI, never silent substitutes).
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 15_000,
    },
  },
});

/** Boot screen while the silent refresh decides the session state (§6.1). */
function BootScreen() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4">
      <Logo />
      <Spinner aria-label="Restoring your session" />
    </div>
  );
}

function AppToaster() {
  const theme = useTheme((s) => s.theme);
  return <Toaster position="bottom-right" richColors closeButton theme={theme} />;
}

export default function App() {
  const status = useAuthStore((s) => s.status);
  const refreshSession = useAuthStore((s) => s.refreshSession);

  // Silent session restore on boot — the refresh cookie does the work (§6.1).
  useEffect(() => {
    void refreshSession();
  }, [refreshSession]);

  // Fail loudly (not silently) if this build is missing its environment
  // configuration (§5.2, §11: env-driven, one build per environment).
  useEffect(() => {
    if (!import.meta.env.VITE_API_BASE_URL) {
      toast.error('Missing build configuration', {
        description:
          'VITE_API_BASE_URL is not set. Copy .env.example to .env.production (dev) or .env.ec2-prod (npm run build:prod).',
        duration: 10_000,
      });
    }
    if (!import.meta.env.VITE_WS_URL) {
      toast.warning('WebSocket not configured', {
        description: 'VITE_WS_URL is not set — live updates are disabled for this build.',
        duration: 10_000,
      });
    }
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <BrowserRouter>
          <RealtimeProvider>
            {status === 'unknown' ? <BootScreen /> : <AppRoutes />}
            <AppToaster />
          </RealtimeProvider>
        </BrowserRouter>
      </MotionConfig>
    </QueryClientProvider>
  );
}
