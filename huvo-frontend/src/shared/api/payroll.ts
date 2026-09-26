import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { queryKeys } from '@/shared/api/queryKeys';
import type { Payslip, SalaryStructure } from '@/shared/types/domain';

/**
 * payroll-service hooks (backend §3.1). Phase 4 on the backend — queries fail
 * honestly until it ships. This data is treated with extra visual restraint
 * in the UI (frontend §6.6): no glass, no playful motion.
 */

export function usePayslips(): UseQueryResult<Payslip[]> {
  return useQuery({
    queryKey: queryKeys.payroll.payslips(),
    queryFn: ({ signal }) => api.get<Payslip[]>(endpoints.payroll.payslips, signal),
    retry: 1,
  });
}

export function useSalaryStructure(): UseQueryResult<SalaryStructure> {
  return useQuery({
    queryKey: queryKeys.payroll.structure(),
    queryFn: ({ signal }) => api.get<SalaryStructure>(endpoints.payroll.structure, signal),
    retry: 1,
  });
}
