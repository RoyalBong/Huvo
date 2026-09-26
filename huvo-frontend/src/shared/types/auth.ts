/** Access Roles used for authorization (backend §4.1) — exactly four. */
export type Role = 'ADMIN' | 'HR' | 'MANAGER' | 'EMPLOYEE';

/** JWT claims issued by identity-service (backend §4.2). */
export interface JwtClaims {
  sub: string;
  role: Role;
  departmentIds: number[];
  employeeId: number;
  iat: number;
  exp: number;
}

/** POST /api/auth/login — shape assumed until identity-service auth lands (Phase 1). */
export interface LoginRequest {
  email: string;
  password: string;
}

/** POST /api/auth/register — same assumption note as LoginRequest. */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

/** Auth responses carry the short-lived access token; the refresh token is an
 *  httpOnly cookie (backend §4.2) and never touches JS. */
export interface AuthResponse {
  accessToken: string;
}
