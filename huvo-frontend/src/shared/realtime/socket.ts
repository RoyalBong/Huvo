import { create } from 'zustand';

/**
 * The single WebSocket client (frontend §5.3) — one connection to
 * notify-service, multiplexed by channel. Never a socket per feature.
 *
 * Wire format assumed until notify-service (backend Phase 5) ships:
 *   client → server: {"action":"subscribe"|"unsubscribe","channel":"..."}
 *   server → client: {"channel":"...","event":"...","payload":{...}}
 *   auth: access token as a query param on the WS handshake.
 *
 * Reconnection is exponential backoff with jitter, capped at 30s, and the
 * status is always surfaced honestly in the UI — never a silent failure that
 * makes stale data look live (frontend §5.3).
 */

export type RealtimeStatus = 'idle' | 'connecting' | 'open' | 'reconnecting';

export interface RealtimeEvent<T = unknown> {
  channel: string;
  event: string;
  payload: T;
}

interface RealtimeState {
  status: RealtimeStatus;
  lastEventAt: string | null;
}

export const useRealtimeStore = create<RealtimeState>(() => ({
  status: 'idle',
  lastEventAt: null,
}));

type Handler = (event: RealtimeEvent) => void;

const MAX_BACKOFF_MS = 30_000;

class HuvoSocket {
  private ws: WebSocket | null = null;
  private url: string | null = null;
  private token: string | null = null;
  private attempt = 0;
  private stopped = true;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly handlers = new Map<string, Set<Handler>>();

  connect(url: string, token: string): void {
    this.url = url;
    this.token = token;
    this.stopped = false;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.open();
  }

  disconnect(): void {
    this.stopped = true;
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.attempt = 0;
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.onerror = null;
      this.ws.onmessage = null;
      this.ws.close();
      this.ws = null;
    }
    useRealtimeStore.setState({ status: 'idle' });
  }

  /** Subscribe to a channel; returns an unsubscribe function. */
  subscribe(channel: string, handler: Handler): () => void {
    let set = this.handlers.get(channel);
    const isFirst = !set;
    if (!set) {
      set = new Set();
      this.handlers.set(channel, set);
    }
    set.add(handler);
    if (isFirst) this.send({ action: 'subscribe', channel });

    return () => {
      const current = this.handlers.get(channel);
      if (!current) return;
      current.delete(handler);
      if (current.size === 0) {
        this.handlers.delete(channel);
        this.send({ action: 'unsubscribe', channel });
      }
    };
  }

  private open(): void {
    if (!this.url || this.stopped) return;

    useRealtimeStore.setState({ status: this.attempt === 0 ? 'connecting' : 'reconnecting' });

    const separator = this.url.includes('?') ? '&' : '?';
    const fullUrl = `${this.url}${separator}token=${encodeURIComponent(this.token ?? '')}`;
    const ws = new WebSocket(fullUrl);
    this.ws = ws;

    ws.onopen = () => {
      this.attempt = 0;
      useRealtimeStore.setState({ status: 'open' });
      // Re-establish every channel the app cares about after a reconnect.
      for (const channel of this.handlers.keys()) {
        this.send({ action: 'subscribe', channel });
      }
    };

    ws.onmessage = (msg) => {
      try {
        const parsed = JSON.parse(String(msg.data)) as RealtimeEvent;
        if (!parsed?.channel) return;
        useRealtimeStore.setState({ lastEventAt: new Date().toISOString() });
        this.handlers.get(parsed.channel)?.forEach((h) => h(parsed));
      } catch {
        // Ignore malformed frames — never surface garbage as live data.
      }
    };

    ws.onerror = () => {
      // onclose always follows; backoff handled there.
    };

    ws.onclose = () => {
      this.ws = null;
      if (this.stopped) return;
      this.attempt += 1;
      useRealtimeStore.setState({ status: 'reconnecting' });
      const backoff = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** (this.attempt - 1));
      const jitter = Math.floor(Math.random() * 500);
      this.retryTimer = setTimeout(() => this.open(), backoff + jitter);
    };
  }

  private send(frame: { action: string; channel: string }): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(frame));
    }
  }

  /** Send an arbitrary JSON frame on the shared connection (e.g. chat.send). */
  sendJson(payload: unknown): boolean {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }
}

export const huvoSocket = new HuvoSocket();
