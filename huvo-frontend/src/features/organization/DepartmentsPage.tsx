import { useMemo, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Building2, MapPin, Pencil, Plus, Trash2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { useAuthStore } from '@/features/auth/authStore';
import {
  useCreateDepartment,
  useDeleteDepartment,
  useDepartments,
  useUpdateDepartment,
} from '@/shared/api/identity';
import { Button } from '@/shared/ui/Button';
import { DataTable } from '@/shared/ui/DataTable';
import { Dialog, DialogContent } from '@/shared/ui/Dialog';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { errorMessage } from '@/shared/types/api';
import type { Department } from '@/shared/types/organization';
import type { ColumnDef } from '@tanstack/react-table';

const departmentSchema = z.object({
  name: z.string().trim().min(2, 'Name must be at least 2 characters'),
  location: z.string().trim().min(1, 'Location is required'),
});

type DepartmentForm = z.infer<typeof departmentSchema>;

/** Department management — real identity-service CRUD (§6.8). */
export default function DepartmentsPage() {
  const role = useAuthStore((s) => s.claims?.role);
  const canManage = role === 'ADMIN' || role === 'HR';

  const departments = useDepartments();
  const createDepartment = useCreateDepartment();
  const updateDepartment = useUpdateDepartment();
  const deleteDepartment = useDeleteDepartment();

  const [editing, setEditing] = useState<Department | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState<Department | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<DepartmentForm>({
    resolver: zodResolver(departmentSchema),
    defaultValues: { name: '', location: '' },
  });

  const openCreate = () => {
    setEditing(null);
    reset({ name: '', location: '' });
    setFormOpen(true);
  };

  const openEdit = (dept: Department) => {
    setEditing(dept);
    reset({ name: dept.name, location: dept.location });
    setFormOpen(true);
  };

  const onSubmit = async (values: DepartmentForm) => {
    try {
      if (editing) {
        await updateDepartment.mutateAsync({ id: editing.id, input: values });
        toast.success('Department updated.');
      } else {
        await createDepartment.mutateAsync(values);
        toast.success('Department created.');
      }
      setFormOpen(false);
    } catch (err) {
      toast.error('Save failed', { description: errorMessage(err) });
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    try {
      await deleteDepartment.mutateAsync(deleting.id);
      toast.success('Department removed.');
      setDeleting(null);
    } catch (err) {
      toast.error('Delete failed', { description: errorMessage(err) });
    }
  };

  const columns: ColumnDef<Department>[] = useMemo(() => {
    const base: ColumnDef<Department>[] = [
      {
        accessorKey: 'id',
        header: 'ID',
        cell: ({ getValue }) => (
          <span className="font-mono text-xs text-muted">#{String(getValue<number>())}</span>
        ),
      },
      {
        accessorKey: 'name',
        header: 'Department',
        cell: ({ row }) => (
          <span className="flex items-center gap-2.5 font-medium">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg border border-edge bg-surface-2">
              <Building2 className="h-3.5 w-3.5 text-primary-ink" aria-hidden />
            </span>
            {row.original.name}
          </span>
        ),
      },
      {
        accessorKey: 'location',
        header: 'Location',
        cell: ({ getValue }) => (
          <span className="flex items-center gap-1.5 text-muted">
            <MapPin className="h-3.5 w-3.5" aria-hidden />
            {String(getValue<string>())}
          </span>
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
              onClick={() => openEdit(row.original)}
            >
              <Pencil className="h-3.5 w-3.5" aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Delete ${row.original.name}`}
              className="hover:text-danger"
              onClick={() => setDeleting(row.original)}
            >
              <Trash2 className="h-3.5 w-3.5" aria-hidden />
            </Button>
          </span>
        ),
      });
    }
    return base;
  }, [canManage]);

  return (
    <div>
      <PageHeader
        title="Departments"
        description="Team structure as maintained in identity-service."
        actions={
          canManage ? (
            <Button size="sm" onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden />
              Add department
            </Button>
          ) : undefined
        }
      />

      <DataTable
        columns={columns}
        data={departments.data}
        isLoading={departments.isLoading}
        error={departments.error}
        onRetry={() => void departments.refetch()}
        searchValue={(d) => `${d.name} ${d.location}`}
        searchPlaceholder="Search departments…"
        emptyTitle="No departments yet"
        emptyDescription="Create your first department to start organizing teams."
        onRowClick={canManage ? openEdit : undefined}
      />

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent
          title={editing ? 'Edit department' : 'Add department'}
          description={editing ? `Update ${editing.name}.` : 'Create a new department.'}
        >
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Input
              label="Name"
              placeholder="Engineering"
              error={errors.name?.message}
              {...register('name')}
            />
            <Input
              label="Location"
              placeholder="Bengaluru"
              error={errors.location?.message}
              {...register('location')}
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="secondary" onClick={() => setFormOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>
                {editing ? 'Save changes' : 'Create'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent
          title="Delete department?"
          description={`This permanently removes ${
            deleting?.name ?? 'this department'
          }. Employees referencing it become unassigned.`}
        >
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={deleteDepartment.isPending}
              onClick={() => void confirmDelete()}
            >
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
