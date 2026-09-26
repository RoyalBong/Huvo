/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Nginx-fronted backend base URL (§5.2). */
  readonly VITE_API_BASE_URL: string;
  /** Single WebSocket endpoint on notify-service (§5.3). */
  readonly VITE_WS_URL: string;
  /** Optional per-service overrides for local dev without nginx (backend §8.1). */
  readonly VITE_API_IDENTITY_URL?: string;
  readonly VITE_API_ATTENDANCE_URL?: string;
  readonly VITE_API_WORKLIFE_URL?: string;
  readonly VITE_API_PAYROLL_URL?: string;
  readonly VITE_API_NOTIFY_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
