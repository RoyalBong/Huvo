import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { queryKeys } from '@/shared/api/queryKeys';
import type { AttendanceDay, LateTracker } from '@/shared/types/domain';

/**
 * attendance-service hooks. The service itself is backend Phase 2 — until it
 * ships these queries fail honestly (ErrorState + retry), never fake data.
 */

export interface AttendanceDashboardRow {
  employeeId: number;
  employeeName: string;
  departmentId: number;
  date: string;
  status: AttendanceDay['status'];
  firstLoginAt: string | null;
}

/** Today's status for the signed-in employee (frontend §6.3). */
export function useTodayAttendance(): UseQueryResult<AttendanceDay> {
  return useQuery({
    queryKey: queryKeys.attendance.today(),
    queryFn: ({ signal }) => api.get<AttendanceDay>(endpoints.attendance.today, signal),
    retry: 1,
  });
}

/** Late-warning / auto-absent transparency trail (frontend §6.3). */
export function useLateTracker(): UseQueryResult<LateTracker[]> {
  return useQuery({
    queryKey: queryKeys.attendance.lateTracker(),
    queryFn: ({ signal }) => api.get<LateTracker[]>(endpoints.attendance.lateTracker, signal),
    retry: 1,
  });
}

/** Personal history for the calendar strip. */
export function useAttendanceHistory(from?: string, to?: string): UseQueryResult<AttendanceDay[]> {
  return useQuery({
    queryKey: queryKeys.attendance.history(from, to),
    queryFn: ({ signal }) => {
      const qs = from && to ? `?from=${from}&to=${to}` : '';
      return api.get<AttendanceDay[]>(`${endpoints.attendance.history}${qs}`, signal);
    },
    retry: 1,
  });
}

/** Real-time, department-grouped dashboard for managers (frontend §6.3). */
export function useAttendanceDashboard(from?: string, to?: string): UseQueryResult<AttendanceDashboardRow[]> {
  return useQuery({
    queryKey: queryKeys.attendance.dashboard(from, to),
    queryFn: ({ signal }) => {
      const qs = from && to ? `?from=${from}&to=${to}` : '';
      return api.get<AttendanceDashboardRow[]>(`${endpoints.attendance.dashboard}${qs}`, signal);
    },
    retry: 1,
  });
}
