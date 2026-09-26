/** The single error body shape for every backend endpoint (backend §10):
 * `{timestamp,status,error,message,path,details?}` — `details` appears only
 * for validation failures. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

/** Normalized client-side error for any failed request (HTTP or network). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: string[],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** True when the failure was a network/reachability problem, not an HTTP error. */
  get isNetwork(): boolean {
    return this.status === 0;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/** Human-readable message for any thrown error — used by ErrorState. */
export function errorMessage(err: unknown): string {
  if (isApiError(err)) return err.message;
  if (err instanceof Error) return err.message;
  return 'Something went wrong.';
}
