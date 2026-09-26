import type { JwtClaims } from '@/shared/types/auth';

/**
 * Decode a JWT payload without verification (signature is verified server-side
 * by every service via huvo-security-lib — backend §4.2). Used only to read
 * claims for UI state; never for authorization decisions.
 */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const [, payload] = token.split('.');
    if (!payload) return null;

    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = atob(padded);
    const claims = JSON.parse(json) as JwtClaims;

    if (!claims.sub || !claims.role) return null;
    return claims;
  } catch {
    return null;
  }
}

/** True when the access token is present and not expired (60s clock skew allowed). */
export function isTokenUsable(token: string | null): boolean {
  if (!token) return false;
  const claims = decodeJwt(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 > Date.now() + 60_000;
}
