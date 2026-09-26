import { useEffect, useRef } from 'react';
import { huvoSocket, type RealtimeEvent } from '@/shared/realtime/socket';

/**
 * Subscribe to a multiplexed channel on the single shared WebSocket.
 * The handler ref is kept fresh so callers never need to memoize.
 */
export function useChannel<T = unknown>(
  channel: string,
  handler: (event: RealtimeEvent<T>) => void,
): void {
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    return huvoSocket.subscribe(channel, (event) => handlerRef.current(event as RealtimeEvent<T>));
  }, [channel]);
}
