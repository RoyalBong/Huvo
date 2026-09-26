import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound, LogOut, Moon, Sun } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAuthStore } from '@/features/auth/authStore';
import { request } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { useEmployee } from '@/shared/api/identity';
import { useTheme } from '@/shared/hooks/useTheme';
import { Avatar } from '@/shared/ui/Avatar';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { errorMessage } from '@/shared/types/api';
import { cn } from '@/shared/utils/cn';

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().min(8, 'At least 8 characters'),
    confirmNewPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmNewPassword, {
    message: 'Passwords do not match',
    path: ['confirmNewPassword'],
  });

type PasswordForm = z.infer<typeof passwordSchema>;

/** Settings: profile, appearance, security, session (§6.10). */
export default function SettingsPage() {
  const claims = useAuthStore((s) => s.claims);
  const logout = useAuthStore((s) => s.logout);
  const theme = useTheme((s) => s.theme);
  const setTheme = useTheme((s) => s.setTheme);
  const navigate = useNavigate();
  const { data: employee } = useEmployee(claims?.employeeId ?? null);

  const name = employee?.name ?? (claims ? `Employee #${claims.employeeId}` : '—');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmNewPassword: '' },
  });

  const onChangePassword = async (values: PasswordForm) => {
    try {
      await request<void>(endpoints.auth.password, {
        method: 'POST',
        body: { currentPassword: values.currentPassword, newPassword: values.newPassword },
      });
      toast.success('Password updated.');
      reset();
    } catch (err) {
      toast.error('Password change failed', { description: errorMessage(err) });
    }
  };

  const themeOptions = [
    { id: 'dark' as const, label: 'Dark', icon: Moon, desc: 'The hero experience' },
    { id: 'light' as const, label: 'Light', icon: Sun, desc: 'Equally complete' },
  ];

  return (
    <div className="max-w-3xl">
      <PageHeader title="Settings" description="Your profile, appearance, and security." />

      {/* Profile */}
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>As known by identity-service.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4">
            <Avatar name={name} size="lg" />
            <div className="min-w-0">
              <p className="truncate text-base font-semibold text-fg">{name}</p>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                <Badge tone="primary">{claims?.role ?? '—'}</Badge>
                <span className="font-mono">ID {claims?.employeeId ?? '—'}</span>
                <span className="font-mono">
                  Depts [{(claims?.departmentIds ?? []).join(', ') || 'none'}]
                </span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Appearance */}
      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Appearance</CardTitle>
          <CardDescription>Dark is the default — light is equally polished.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-2 sm:max-w-md">
            {themeOptions.map((opt) => {
              const Icon = opt.icon;
              const isActive = theme === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setTheme(opt.id)}
                  aria-pressed={isActive}
                  className={cn(
                    'flex flex-col items-center gap-1.5 rounded-lg border p-3 transition-colors',
                    isActive
                      ? 'border-primary/60 bg-primary/10 text-fg'
                      : 'border-edge bg-surface-2/60 text-muted hover:bg-surface-2',
                  )}
                >
                  <Icon className="h-4 w-4" aria-hidden />
                  <span className="text-xs font-medium">{opt.label}</span>
                  <span className="text-center text-[10px] text-faint">{opt.desc}</span>
                </button>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Security */}
      <Card className="mt-4">
        <CardHeader>
          <CardTitle>
            <span className="inline-flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-primary-ink" aria-hidden />
              Change password
            </span>
          </CardTitle>
          <CardDescription>Access tokens rotate automatically after a change.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onChangePassword)} className="space-y-4" noValidate>
            <Input
              label="Current password"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword')}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="New password"
                type="password"
                autoComplete="new-password"
                error={errors.newPassword?.message}
                {...register('newPassword')}
              />
              <Input
                label="Confirm new password"
                type="password"
                autoComplete="new-password"
                error={errors.confirmNewPassword?.message}
                {...register('confirmNewPassword')}
              />
            </div>
            <Button type="submit" loading={isSubmitting}>
              Update password
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Session */}
      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Session</CardTitle>
          <CardDescription>
            Signing out clears the access token and revokes the refresh cookie.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="secondary"
            onClick={() => {
              void logout().then(() => navigate('/login'));
            }}
          >
            <LogOut className="h-4 w-4" aria-hidden />
            Sign out
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
