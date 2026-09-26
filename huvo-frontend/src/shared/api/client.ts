import { ApiError, type ApiErrorBody } from '@/shared/types/api';
import { useAuthStore } from '@/features/auth/authStore';

/**
 * The single HTTP entry point (frontend §3: native fetch + TanStack Query).
 *
 * - Base URL comes from environment only (§5.2) — never a literal in source.
 * - Per-service overrides exist for local dev without nginx (backend §8.1);
 *   in production everything resolves against the one Nginx-fronted base URL.
 * - Access token is sent as `Authorization: Bearer <token>`; the refresh token
 *   is an httpOnly cookie sent with `credentials: 'include'` (backend §4.2).
 * - On a 401 a single-flight silent refresh runs, then the request retries once.
 */

const SERVICE_OVERRIDES: ReadonlyArray<{ readonly prefixes: readonly string[]; readonly base: string | undefined }> = [
  { prefixes: ['/api/auth', '/api/employees', '/api/departments'], base: import.meta.env.VITE_API_IDENTITY_URL },
  { prefixes: ['/api/attendance'], base: import.meta.env.VITE_API_ATTENDANCE_URL },
  { prefixes: ['/api/tasks', '/api/leave', '/api/documents'], base: import.meta.env.VITE_API_WORKLIFE_URL },
  { prefixes: ['/api/payroll'], base: import.meta.env.VITE_API_PAYROLL_URL },
  { prefixes: ['/api/notify'], base: import.meta.env.VITE_API_NOTIFY_URL },
];

/** Resolve a backend path to a full URL for the current environment. */
export function resolveUrl(path: string): string {
  const override = SERVICE_OVERRIDES.find((s) => s.prefixes.some((p) => path === p || path.startsWith(`${p}/`)));
  const base = override?.base || import.meta.env.VITE_API_BASE_URL;
  return `${base.replace(/\/$/, '')}${path}`;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Attach the Bearer token and participate in silent refresh (default true). */
  auth?: boolean;
  signal?: AbortSignal;
  /** Internal: this attempt is already a post-refresh retry. */
  retried?: boolean;
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> | null = null;
  try {
    body = (await res.json()) as ApiErrorBody;
  } catch {
    body = null;
  }
  return new ApiError(
    res.status,
    body?.error ?? res.statusText ?? 'ERROR',
    body?.message ?? `Request failed with status ${res.status}.`,
    body?.details,
  );
}

/** Core request. Prefer the TanStack hooks in `shared/api/*` over calling this directly. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, signal, retried = false } = options;
  const token = useAuthStore.getState().accessToken;

  const headers: Headers = new Headers({ Accept: 'application/json' });
  if (body !== undefined) headers.set('Content-Type', 'application/json');
  if (auth && token) headers.set('Authorization', `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(resolveUrl(path), {
      method,
      headers,
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err;
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the Huvo backend. Check your connection and try again.');
  }

  // Silent refresh path: one refresh across the app, then replay the request once.
  if (res.status === 401 && auth && !retried && !path.startsWith('/api/auth/')) {
    const refreshed = await useAuthStore.getState().refreshSession();
    if (refreshed) return request<T>(path, { ...options, retried: true });
    useAuthStore.getState().clearSession();
  }

  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
