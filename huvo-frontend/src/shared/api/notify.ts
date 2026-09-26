import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { queryKeys } from '@/shared/api/queryKeys';
import type { AppNotification, ChatConversation, ChatMessage } from '@/shared/types/domain';

/** notify-service hooks (backend §3.1 — Phase 5). */

export function useNotifications(): UseQueryResult<AppNotification[]> {
  return useQuery({
    queryKey: queryKeys.notify.notifications(),
    queryFn: ({ signal }) => api.get<AppNotification[]>(endpoints.notify.notifications, signal),
    retry: 1,
  });
}

export function useMarkNotificationRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<void>(`/api/notify/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.notify.notifications() }),
  });
}

export function useConversations(): UseQueryResult<ChatConversation[]> {
  return useQuery({
    queryKey: queryKeys.notify.conversations(),
    queryFn: ({ signal }) => api.get<ChatConversation[]>(endpoints.notify.conversations, signal),
    retry: 1,
  });
}

export function useMessages(conversationId: string | null): UseQueryResult<ChatMessage[]> {
  return useQuery({
    queryKey: queryKeys.notify.messages(conversationId ?? ''),
    queryFn: ({ signal }) => api.get<ChatMessage[]>(endpoints.notify.messages(conversationId!), signal),
    enabled: conversationId !== null,
    retry: 1,
  });
}
