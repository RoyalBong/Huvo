import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { queryKeys } from '@/shared/api/queryKeys';
import type { LeaveBalance, LeaveRequest, OnboardingItem, Task, TaskInput } from '@/shared/types/domain';

/**
 * worklife-service hooks — tasks, leave, onboarding (backend §3.1).
 * The service is backend Phase 3; queries fail honestly until it ships.
 */

/* ---------- Tasks ---------- */

export function useMyTasks(): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: queryKeys.worklife.myTasks(),
    queryFn: ({ signal }) => api.get<Task[]>(endpoints.taskMine, signal),
    retry: 1,
  });
}

export function useTeamTasks(enabled = true): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: queryKeys.worklife.teamTasks(),
    queryFn: ({ signal }) => api.get<Task[]>(endpoints.taskTeam, signal),
    enabled,
    retry: 1,
  });
}

export function useCreateTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TaskInput) => api.post<Task>(endpoints.tasks, input),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

/** Status change — optimistic on the caller's side, reconciled by invalidation (§8). */
export function useUpdateTaskStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: Task['status'] }) =>
      api.patch<Task>(`${endpoints.tasks}/${id}/status`, { status }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

/** Ask worklife-service for an S3 pre-signed upload URL (backend §6.2 — the
 *  client uploads directly to S3, never through the app server). */
export function useTaskSubmissionUpload() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ taskId, file }: { taskId: number; file: File }) => {
      const url = await api.post<{ uploadUrl: string; objectKey: string }>(
        `${endpoints.tasks}/${taskId}/submission-url`,
        { fileName: file.name, contentType: file.type },
      );
      const put = await fetch(url.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file,
      });
      if (!put.ok) throw new Error('S3 upload failed.');
      return api.post<Task>(`${endpoints.tasks}/${taskId}/submit`, { objectKey: url.objectKey });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

/* ---------- Leave ---------- */

export function useLeaveRequests(): UseQueryResult<LeaveRequest[]> {
  return useQuery({
    queryKey: queryKeys.worklife.leaveRequests(),
    queryFn: ({ signal }) => api.get<LeaveRequest[]>(endpoints.leave, signal),
    retry: 1,
  });
}

export function useLeaveBalance(): UseQueryResult<LeaveBalance[]> {
  return useQuery({
    queryKey: queryKeys.worklife.leaveBalance(),
    queryFn: ({ signal }) => api.get<LeaveBalance[]>(endpoints.leaveBalance, signal),
    retry: 1,
  });
}

export function useApplyLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { type: string; fromDate: string; toDate: string; reason: string }) =>
      api.post<LeaveRequest>(endpoints.leave, input),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['leave'] });
    },
  });
}

export function useDecideLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision }: { id: number; decision: 'APPROVED' | 'REJECTED' }) =>
      api.patch<LeaveRequest>(`${endpoints.leave}/${id}/decision`, { decision }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['leave'] });
    },
  });
}

/* ---------- Onboarding ---------- */

export function useOnboardingChecklist(): UseQueryResult<OnboardingItem[]> {
  return useQuery({
    queryKey: queryKeys.worklife.onboarding(),
    queryFn: ({ signal }) => api.get<OnboardingItem[]>(endpoints.onboarding, signal),
    retry: 1,
  });
}

export function useCompleteOnboardingItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, objectKey }: { id: number; objectKey?: string }) =>
      api.post<OnboardingItem>(`${endpoints.onboarding}/${id}/complete`, { objectKey }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.worklife.onboarding() });
    },
  });
}
