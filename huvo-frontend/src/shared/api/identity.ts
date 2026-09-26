import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { queryKeys } from '@/shared/api/queryKeys';
import type { Department, DepartmentInput, Employee, EmployeeInput } from '@/shared/types/organization';

/* ---------- Employees (identity-service — real, implemented endpoints) ---------- */

export function useEmployees(): UseQueryResult<Employee[]> {
  return useQuery({
    queryKey: queryKeys.identity.employees(),
    queryFn: ({ signal }) => api.get<Employee[]>(endpoints.employees, signal),
    retry: 1,
  });
}

export function useEmployee(id: number | null): UseQueryResult<Employee> {
  return useQuery({
    queryKey: queryKeys.identity.employee(id ?? -1),
    queryFn: ({ signal }) => api.get<Employee>(`${endpoints.employees}/${id}`, signal),
    enabled: id !== null && id > 0,
    retry: 1,
  });
}

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: EmployeeInput) => api.post<Employee>(endpoints.employees, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.employees() }),
  });
}

export function useUpdateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: EmployeeInput }) =>
      api.put<Employee>(`${endpoints.employees}/${id}`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.employees() }),
  });
}

export function useDeleteEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`${endpoints.employees}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.employees() }),
  });
}

/* ---------- Departments (identity-service — real, implemented endpoints) ---------- */

export function useDepartments(): UseQueryResult<Department[]> {
  return useQuery({
    queryKey: queryKeys.identity.departments(),
    queryFn: ({ signal }) => api.get<Department[]>(endpoints.departments, signal),
    retry: 1,
  });
}

export function useCreateDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: DepartmentInput) => api.post<Department>(endpoints.departments, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.departments() }),
  });
}

export function useUpdateDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: DepartmentInput }) =>
      api.put<Department>(`${endpoints.departments}/${id}`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.departments() }),
  });
}

export function useDeleteDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`${endpoints.departments}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.identity.departments() }),
  });
}
