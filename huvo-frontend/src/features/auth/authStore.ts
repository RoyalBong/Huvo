import { create } from 'zustand';
import { request } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import type { AuthResponse, JwtClaims, LoginRequest, RegisterRequest } from '@/shared/types/auth';
import { decodeJwt, isTokenUsable } from '@/shared/utils/jwt';

/**
 * Auth client state (frontend §5.4 / backend §4.2).
 *
 * - The short-lived access token lives in memory only — never localStorage.
 * - The refresh token is an httpOnly cookie; on app start we attempt one
 *   silent refresh ("silent refresh where possible", frontend §6.1).
 * - Refresh is single-flight: concurrent 401s share one in-flight promise.
 */
type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

interface AuthState {
  accessToken: string | null;
  claims: JwtClaims | null;
  status: AuthStatus;
  /** Set by the client on 401 → refresh → retry orchestration. */
  refreshSession: () => Promise<boolean>;
  setSession: (token: string) => void;
  clearSession: () => void;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (input: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

let refreshInFlight: Promise<boolean> | null = null;

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  claims: null,
  status: 'unknown',

  setSession: (token) => {
    set({ accessToken: token, claims: decodeJwt(token), status: 'authenticated' });
  },

  clearSession: () => {
    set({ accessToken: null, claims: null, status: 'anonymous' });
  },

  refreshSession: async () => {
    if (!refreshInFlight) {
      refreshInFlight = (async () => {
        try {
          // The refresh cookie rides along via credentials: 'include'.
          const res = await request<AuthResponse>(endpoints.auth.refresh, {
            method: 'POST',
            auth: false,
          });
          if (res?.accessToken && isTokenUsable(res.accessToken)) {
            get().setSession(res.accessToken);
            return true;
          }
          get().clearSession();
          return false;
        } catch {
          get().clearSession();
          return false;
        } finally {
          refreshInFlight = null;
        }
      })();
    }
    return refreshInFlight;
  },

  login: async (credentials) => {
    const res = await request<AuthResponse>(endpoints.auth.login, {
      method: 'POST',
      body: credentials,
      auth: false,
    });
    if (!res?.accessToken) {
      throw new Error('Login succeeded but no access token was returned.');
    }
    get().setSession(res.accessToken);
  },

  register: async (input) => {
    const res = await request<AuthResponse>(endpoints.auth.register, {
      method: 'POST',
      body: input,
      auth: false,
    });
    if (res?.accessToken) get().setSession(res.accessToken);
  },

  logout: async () => {
    try {
      await request<void>(endpoints.auth.logout, { method: 'POST' });
    } catch {
      // Even if the server call fails, drop the local session.
    }
    get().clearSession();
  },
}));
