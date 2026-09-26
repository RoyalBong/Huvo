import { useMemo, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAuthStore } from '@/features/auth/authStore';
import {
  useCreateEmployee,
  useDeleteEmployee,
  useDepartments,
  useEmployees,
  useUpdateEmployee,
} from '@/shared/api/identity';
import { Avatar } from '@/shared/ui/Avatar';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { DataTable } from '@/shared/ui/DataTable';
import { Dialog, DialogContent } from '@/shared/ui/Dialog';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { errorMessage } from '@/shared/types/api';
import type { Employee } from '@/shared/types/organization';
import { formatNumber } from '@/shared/utils/format';
import type { ColumnDef } from '@tanstack/react-table';

const employeeSchema = z.object({
  name: z.string().trim().min(2, 'Name must be at least 2 characters'),
  departmentId: z.string().trim().optional().nullable(),
  salary: z.coerce.number().min(0, 'Salary cannot be negative'),
});

type EmployeeForm = z.infer<typeof employeeSchema>;

/**
 * Employee directory — a real structured table (§4.4: not a bento grid of
 * cards; must scan cleanly at 200 employees), backed by the live
 * identity-service CRUD endpoints. Visibility & actions gated by Access Role
 * exactly as the API would allow (§6.8).
 */
export default function EmployeesPage() {
  const role = useAuthStore((s) => s.claims?.role);
  const canManage = role === 'ADMIN' || role === 'HR';

  const employees = useEmployees();
  const departments = useDepartments();
  const createEmployee = useCreateEmployee();
  const updateEmployee = useUpdateEmployee();
  const deleteEmployee = useDeleteEmployee();

  const [editing, setEditing] = useState<Employee | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<Employee | null>(null);

  const deptById = useMemo(() => {
    const map = new Map<string, string>();
    for (const d of departments.data ?? []) map.set(String(d.id), d.name);
    return map;
  }, [departments.data]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<EmployeeForm>({
    resolver: zodResolver(employeeSchema),
    defaultValues: { name: '', departmentId: '', salary: 0 },
  });

  const openCreate = () => {
    setEditing(null);
    reset({ name: '', departmentId: '', salary: 0 });
    setFormOpen(true);
  };

  const openEdit = (employee: Employee) => {
    setEditing(employee);
    reset({
      name: employee.name,
      departmentId: employee.departmentId ?? '',
      salary: employee.salary,
    });
    setFormOpen(true);
  };

  const onSubmit = async (values: EmployeeForm) => {
    const input = {
      name: values.name,
      departmentId: values.departmentId ? values.departmentId : null,
      salary: values.salary,
    };
    try {
      if (editing) {
        await updateEmployee.mutateAsync({ id: editing.id, input });
        toast.success('Employee updated.');
      } else {
        await createEmployee.mutateAsync(input);
        toast.success('Employee added.');
      }
      setFormOpen(false);
    } catch (err) {
      toast.error('Save failed', { description: errorMessage(err) });
    }
  };

  const confirmDelete = async () => {
    const target = editing ?? deleting;
    if (!target) return;
    try {
      await deleteEmployee.mutateAsync(target.id);
      toast.success('Employee removed.');
      setDeleting(null);
      setFormOpen(false);
    } catch (err) {
      toast.error('Delete failed', { description: errorMessage(err) });
    }
  };

  const columns: ColumnDef<Employee>[] = useMemo(() => {
    const base: ColumnDef<Employee>[] = [
      {
        accessorKey: 'id',
        header: 'ID',
        cell: ({ getValue }) => (
          <span className="font-mono text-xs text-muted">#{String(getValue<number>())}</span>
        ),
      },
      {
        accessorKey: 'name',
        header: 'Name',
        cell: ({ row }) => (
          <span className="flex items-center gap-2.5">
            <Avatar name={row.original.name} size="sm" />
            <span className="font-medium">{row.original.name}</span>
          </span>
        ),
      },
      {
        accessorKey: 'departmentId',
        header: 'Department',
        cell: ({ getValue }) => {
          const id = getValue<string | null>();
          if (!id) return <span className="text-faint">Unassigned</span>;
          return <Badge tone="neutral">{deptById.get(id) ?? `Dept ${id}`}</Badge>;
        },
      },
      {
        accessorKey: 'salary',
        header: 'Salary',
        cell: ({ getValue }) => (
          <span className="font-mono text-xs">{formatNumber(getValue<number>())}</span>
        ),
      },
    ];

    if (canManage) {
      base.push({
        id: 'actions',
        header: '',
        cell: ({ row }) => (
          <span className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Edit ${row.original.name}`}
              onClick={(e) => {
                e.stopPropagation();
                openEdit(row.original);
              }}
            >
              <Pencil className="h-3.5 w-3.5" aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Delete ${row.original.name}`}
              className="hover:text-danger"
              onClick={(e) => {
                e.stopPropagation();
                setEditing(null);
                setDeleting(row.original);
              }}
            >
              <Trash2 className="h-3.5 w-3.5" aria-hidden />
            </Button>
          </span>
        ),
      });
    }
    return base;
  }, [canManage, deptById]);

  return (
    <div>
      <PageHeader
        title="Employee directory"
        description="Every employee record, straight from identity-service."
        actions={
          canManage ? (
            <Button size="sm" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden />
              Add employee
            </Button>
          ) : undefined
        }
      />

      <DataTable
        columns={columns}
        data={employees.data}
        isLoading={employees.isLoading}
        error={employees.error}
        onRetry={() => void employees.refetch()}
        searchValue={(e) => `${e.name} ${deptById.get(e.departmentId ?? '') ?? ''}`}
        searchPlaceholder="Search employees…"
        emptyTitle="No employees yet"
        emptyDescription="Add your first employee to populate the directory."
        onRowClick={canManage ? openEdit : undefined}
      />

      {/* Create / edit */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent
          title={editing ? 'Edit employee' : 'Add employee'}
          description={
            editing
              ? `Update the record for ${editing.name}.`
              : 'Create a new employee record in identity-service.'
          }
        >
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Input
              label="Full name"
              placeholder="Jane Doe"
              error={errors.name?.message}
              {...register('name')}
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="emp-dept" className="text-sm font-medium text-fg">
                Department
              </label>
              <select
                id="emp-dept"
                className="h-9 w-full rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
                {...register('departmentId')}
              >
                <option value="">Unassigned</option>
                {(departments.data ?? []).map((d) => (
                  <option key={d.id} value={String(d.id)}>
                    {d.name}
                  </option>
                ))}
              </select>
              {errors.departmentId && (
                <p role="alert" className="text-xs text-danger">
                  {errors.departmentId.message}
                </p>
              )}
            </div>
            <Input
              label="Salary"
              type="number"
              step="any"
              min="0"
              placeholder="0"
              error={errors.salary?.message}
              hint="Annual figure as stored by identity-service."
              {...register('salary')}
            />

            <div className="flex items-center justify-between pt-2">
              {editing ? (
                <Button
                  type="button"
                  variant="ghost"
                  className="text-danger hover:text-danger"
                  onClick={() => {
                    setFormOpen(false);
                    setDeleting(editing);
                  }}
                >
                  <Trash2 className="h-4 w-4" aria-hidden />
                  Delete
                </Button>
              ) : (
                <span />
              )}
              <div className="flex gap-2">
                <Button type="button" variant="secondary" onClick={() => setFormOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" loading={isSubmitting}>
                  {editing ? 'Save changes' : 'Add employee'}
                </Button>
              </div>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <Dialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent
          title="Delete employee?"
          description={`This permanently removes ${
            deleting?.name ?? 'this employee'
          } from identity-service. This can't be undone.`}
        >
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={deleteEmployee.isPending} onClick={() => void confirmDelete()}>
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

