import { useEffect, useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { MessagesSquare, Send } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { useConversations, useMessages } from '@/shared/api/notify';
import { useChannel } from '@/shared/realtime/useChannel';
import { huvoSocket, useRealtimeStore } from '@/shared/realtime/socket';
import { Avatar } from '@/shared/ui/Avatar';
import { Button } from '@/shared/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/Card';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Spinner } from '@/shared/ui/Skeleton';
import { LiveDot } from '@/shared/realtime/ReconnectBanner';
import type { ChatMessage } from '@/shared/types/domain';
import { formatRelative, formatTime } from '@/shared/utils/format';
import { cn } from '@/shared/utils/cn';
import { toast } from 'sonner';

/**
 * Internal chat (§6.7): 1:1 and group conversations over the SHARED WebSocket
 * connection — presence/typing come through the same 'chat' channel. Modern
 * messaging UI, deliberately not a social feed.
 *
 * Sending is optimistic (§8): the message appears immediately as "sending"
 * and is reconciled against the authoritative event when the server echoes it.
 */
export default function ChatPage() {
  const claims = useAuthStore((s) => s.claims);
  const queryClient = useQueryClient();

  const conversations = useConversations();
  const [activeId, setActiveId] = useState<string | null>(null);
  const messages = useMessages(activeId);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState<ChatMessage[]>([]);

  // Any chat event → reconcile against the server's authoritative list (§8).
  useChannel('chat', () => {
    void queryClient.invalidateQueries({ queryKey: ['notify'] });
  });

  // Drop optimistic entries once the server copy arrives.
  useEffect(() => {
    if (!messages.data || pending.length === 0) return;
    const bodies = new Set(messages.data.map((m) => m.body));
    setPending((prev) => prev.filter((p) => !bodies.has(p.body)));
  }, [messages.data, pending.length]);

  const active = (conversations.data ?? []).find((c) => c.id === activeId) ?? null;
  const allMessages = [...(messages.data ?? []), ...pending];

  const send = (e: FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body || !activeId || !claims) return;

    if (useRealtimeStore.getState().status !== 'open') {
      toast.error('Not connected', { description: 'The connection is reconnecting — try again in a moment.' });
      return;
    }

    const temp: ChatMessage = {
      id: `pending-${Date.now()}`,
      conversationId: activeId,
      senderId: claims.employeeId,
      senderName: 'You',
      body,
      sentAt: new Date().toISOString(),
    };
    setPending((prev) => [...prev, temp]);
    huvoSocket.sendJson({ action: 'chat.send', conversationId: activeId, body });
    setDraft('');
  };

  return (
    <div>
      <PageHeader
        title="Chat"
        description="Direct and team conversations over the shared connection."
        actions={<LiveDot className="mx-1" />}
      />

      <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
        {/* Conversations */}
        <Card className="flex max-h-[70vh] flex-col">
          <CardHeader>
            <CardTitle>Conversations</CardTitle>
          </CardHeader>
          <CardContent className="flex-1 overflow-y-auto p-2 pt-0">
            {conversations.isLoading ? (
              <div className="flex justify-center py-6">
                <Spinner />
              </div>
            ) : conversations.error ? (
              <ErrorState
                error={conversations.error}
                onRetry={() => void conversations.refetch()}
                className="py-6"
              />
            ) : (conversations.data ?? []).length === 0 ? (
              <EmptyState
                icon={MessagesSquare}
                title="No conversations"
                description="Direct and department chats appear here."
                className="py-6"
              />
            ) : (
              <ul className="space-y-1">
                {(conversations.data ?? []).map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => setActiveId(c.id)}
                      aria-pressed={activeId === c.id}
                      className={cn(
                        'flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors',
                        activeId === c.id
                          ? 'bg-surface-2 text-fg shadow-soft'
                          : 'text-muted hover:bg-surface-2/60 hover:text-fg',
                      )}
                    >
                      <Avatar name={c.label} size="sm" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{c.label}</p>
                        <p className="truncate text-[10px] text-faint">
                          {c.isGroup ? `${c.memberIds.length} members` : 'Direct'}
                          {c.lastMessageAt ? ` · ${formatRelative(c.lastMessageAt)}` : ''}
                        </p>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* Thread */}
        <Card className="flex max-h-[70vh] min-h-[420px] flex-col">
          {!activeId ? (
            <EmptyState
              icon={MessagesSquare}
              title="Pick a conversation"
              description="Select a conversation on the left to read and send messages."
              className="my-auto"
            />
          ) : (
            <>
              <CardHeader className="flex-row items-center justify-between border-b border-edge/60 pb-3">
                <CardTitle>{active?.label ?? 'Conversation'}</CardTitle>
                <LiveDot />
              </CardHeader>
              <CardContent className="flex-1 overflow-y-auto py-4">
                {messages.isLoading ? (
                  <div className="flex justify-center py-8">
                    <Spinner />
                  </div>
                ) : messages.error ? (
                  <ErrorState error={messages.error} onRetry={() => void messages.refetch()} />
                ) : allMessages.length === 0 ? (
                  <EmptyState
                    title="No messages yet"
                    description="Say hello — messages appear live for everyone in this conversation."
                    className="py-8"
                  />
                ) : (
                  <ul className="space-y-3">
                    {allMessages.map((m) => {
                      const mine = m.senderId === claims?.employeeId;
                      const optimistic = m.id.startsWith('pending-');
                      return (
                        <li key={m.id} className={cn('flex gap-2.5', mine && 'flex-row-reverse')}>
                          <Avatar name={m.senderName} size="sm" />
                          <div
                            className={cn(
                              'max-w-[75%] rounded-2xl border px-3 py-2',
                              mine ? 'border-primary/25 bg-primary/15' : 'border-edge/60 bg-surface-2',
                            )}
                          >
                            {!mine && (
                              <p className="text-[11px] font-medium text-primary-ink">
                                {m.senderName}
                              </p>
                            )}
                            <p
                              className={cn(
                                'text-sm leading-relaxed text-fg',
                                optimistic && 'opacity-70',
                              )}
                            >
                              {m.body}
                            </p>
                            <p className="mt-0.5 text-right font-mono text-[9px] text-faint">
                              {optimistic ? 'sending…' : formatTime(m.sentAt)}
                            </p>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </CardContent>

              <form onSubmit={send} className="flex items-center gap-2 border-t border-edge/60 p-3">
                <input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder="Write a message…"
                  aria-label="Message"
                  className="h-9 flex-1 rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg placeholder:text-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                />
                <Button type="submit" size="icon" disabled={!draft.trim()} aria-label="Send message">
                  <Send className="h-4 w-4" aria-hidden />
                </Button>
              </form>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
