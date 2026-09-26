import { useMemo, useState, type ReactNode } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  useReactTable,
  type ColumnDef,
} from '@tanstack/react-table';
import { Search, type LucideIcon } from 'lucide-react';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { SkeletonRows } from '@/shared/ui/Skeleton';
import { cn } from '@/shared/utils/cn';

/**
 * Dense structured table for tabular content — employee directory, payslip
 * history, audit logs (§4.4: bento/card patterns are the WRONG fit here).
 * Loading / empty / error are first-class (§9).
 */
interface DataTableProps<T> {
  columns: ColumnDef<T>[];
  data: T[] | undefined;
  isLoading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyIcon?: LucideIcon;
  /** Enables the built-in search box when provided. */
  searchValue?: (row: T) => string;
  searchPlaceholder?: string;
  toolbar?: ReactNode;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns,
  data,
  isLoading = false,
  error,
  onRetry,
  emptyTitle = 'Nothing here yet',
  emptyDescription,
  emptyIcon,
  searchValue,
  searchPlaceholder = 'Search…',
  toolbar,
  onRowClick,
}: DataTableProps<T>) {
  const [globalFilter, setGlobalFilter] = useState('');

  const rows = useMemo(() => data ?? [], [data]);

  const table = useReactTable({
    data: rows,
    columns,
    state: { globalFilter },
    onGlobalFilterChange: setGlobalFilter,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    globalFilterFn: searchValue
      ? (row, _columnId, value) =>
          searchValue(row.original).toLowerCase().includes(String(value).toLowerCase())
      : undefined,
  });

  const showToolbar = Boolean(searchValue || toolbar);

  return (
    <div className="overflow-hidden rounded-card border border-edge bg-surface-1">
      {showToolbar && (
        <div className="flex flex-wrap items-center gap-2 border-b border-edge/60 px-3 py-2.5">
          {searchValue && (
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-faint" aria-hidden />
              <input
                value={globalFilter}
                onChange={(e) => setGlobalFilter(e.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
                className="h-8 w-56 rounded-lg border border-edge bg-surface-2 pl-8 pr-3 text-xs text-fg placeholder:text-faint focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60"
              />
            </div>
          )}
          {toolbar && <div className="ml-auto flex items-center gap-2">{toolbar}</div>}
        </div>
      )}

      {isLoading ? (
        <SkeletonRows rows={6} />
      ) : error ? (
        <ErrorState error={error} onRetry={onRetry} />
      ) : table.getRowModel().rows.length === 0 ? (
        <EmptyState
          icon={emptyIcon}
          title={globalFilter ? 'No matches' : emptyTitle}
          description={
            globalFilter
              ? 'Try a different search term.'
              : emptyDescription ?? 'When data exists, it will appear here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <th
                      key={header.id}
                      scope="col"
                      className="border-b border-edge bg-surface-2/60 px-3 py-2 text-left text-[11px] font-medium uppercase tracking-wider text-faint"
                    >
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody>
              {table.getRowModel().rows.map((row) => (
                <tr
                  key={row.id}
                  onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                  className={cn(
                    'border-b border-edge/40 transition-colors last:border-b-0',
                    onRowClick && 'cursor-pointer hover:bg-surface-2/70',
                  )}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-3 py-2.5 align-middle text-fg">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
