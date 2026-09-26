import { Download, Wallet } from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/features/auth/authStore';
import { resolveUrl } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { usePayslips, useSalaryStructure } from '@/shared/api/payroll';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { DataTable } from '@/shared/ui/DataTable';
import { PageHeader } from '@/shared/ui/PageHeader';
import { errorMessage } from '@/shared/types/api';
import type { Payslip } from '@/shared/types/domain';
import { formatNumber, formatRelative } from '@/shared/utils/format';
import type { ColumnDef } from '@tanstack/react-table';

/**
 * Payroll (§6.6) — the calmest, most trustworthy screen in the product:
 * sensitive financial data gets NO glassmorphism and NO playful motion.
 * Solid surfaces, plain numbers, clarity over polish — specifically here.
 */
export default function PayrollPage() {
  const structure = useSalaryStructure();
  const payslips = usePayslips();

  // Download goes through the API with the user's token; the object itself
  // comes from S3 (payslips are stored there — backend §3.2).
  const download = async (payslip: Payslip) => {
    try {
      const token = useAuthStore.getState().accessToken;
      const res = await fetch(resolveUrl(`${endpoints.payroll.payslips}/${payslip.id}/download`), {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        credentials: 'include',
      });
      if (!res.ok) throw new Error(`Download failed (HTTP ${res.status}).`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `payslip-${payslip.period}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error('Download failed', { description: errorMessage(err) });
    }
  };

  const columns: ColumnDef<Payslip>[] = [
    {
      accessorKey: 'period',
      header: 'Period',
      cell: ({ getValue }) => (
        <span className="font-mono text-xs">{String(getValue<string>())}</span>
      ),
    },
    {
      accessorKey: 'grossPay',
      header: 'Gross',
      cell: ({ getValue }) => (
        <span className="font-mono text-xs">{formatNumber(getValue<number>())}</span>
      ),
    },
    {
      accessorKey: 'deductions',
      header: 'Deductions',
      cell: ({ getValue }) => (
        <span className="font-mono text-xs text-danger">−{formatNumber(getValue<number>())}</span>
      ),
    },
    {
      accessorKey: 'netPay',
      header: 'Net pay',
      cell: ({ getValue }) => (
        <span className="font-mono text-sm font-semibold">{formatNumber(getValue<number>())}</span>
      ),
    },
    {
      accessorKey: 'generatedAt',
      header: 'Generated',
      cell: ({ getValue }) => (
        <span className="text-xs text-faint">{formatRelative(getValue<string>())}</span>
      ),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <span className="flex justify-end">
          <Button
            size="sm"
            variant="secondary"
            onClick={() => void download(row.original)}
            aria-label={`Download payslip for ${row.original.period}`}
          >
            <Download className="h-3.5 w-3.5" aria-hidden />
            PDF
          </Button>
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Payroll"
        description="Salary structure and payslip history — handled with extra care."
      />

      {/* Salary structure — solid surfaces only (§6.6) */}
      <Card plain>
        <CardHeader>
          <CardTitle>
            <span className="inline-flex items-center gap-2">
              <Wallet className="h-4 w-4 text-primary-ink" aria-hidden />
              Salary structure
            </span>
          </CardTitle>
          <CardDescription>As maintained by payroll-service.</CardDescription>
        </CardHeader>
        <CardContent>
          {structure.isLoading ? (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-20 animate-pulse rounded-lg bg-surface-2" />
              ))}
            </div>
          ) : structure.error ? (
            <div className="py-4 text-sm text-muted" role="alert">
              Salary structure is unavailable — payroll-service may not be running yet.{' '}
              <button
                type="button"
                className="text-primary-ink underline-offset-2 hover:underline"
                onClick={() => void structure.refetch()}
              >
                Retry
              </button>
            </div>
          ) : !structure.data ? (
            <p className="py-4 text-sm text-muted">No salary structure recorded for you yet.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {(
                [
                  ['Base', structure.data.base],
                  ['Allowances', structure.data.allowances],
                  ['Deductions', structure.data.deductions],
                  ['CTC', structure.data.ctc],
                ] as const
              ).map(([label, value]) => (
                <div key={label} className="rounded-lg border border-edge bg-surface-2/60 p-4">
                  <p className="text-xs text-muted">{label}</p>
                  <p className="mt-1 font-mono text-xl font-semibold tracking-tight text-fg">
                    {formatNumber(value)}
                  </p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Payslip history */}
      <div className="mt-4">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-fg">Payslip history</h2>
          <Badge tone="neutral">Monthly</Badge>
        </div>
        <DataTable
          columns={columns}
          data={payslips.data}
          isLoading={payslips.isLoading}
          error={payslips.error}
          onRetry={() => void payslips.refetch()}
          searchValue={(p) => p.period}
          searchPlaceholder="Filter by period…"
          emptyTitle="No payslips yet"
          emptyDescription="Generated payslips appear here each month and stay downloadable."
        />
      </div>
    </div>
  );
}
