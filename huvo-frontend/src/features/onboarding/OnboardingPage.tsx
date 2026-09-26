import { useRef, useState } from 'react';
import { CheckCircle2, Circle, ClipboardList, Upload } from 'lucide-react';
import { api } from '@/shared/api/client';
import { endpoints } from '@/shared/api/endpoints';
import { useCompleteOnboardingItem, useOnboardingChecklist } from '@/shared/api/worklife';
import { Button } from '@/shared/ui/Button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card';
import { EmptyState, ErrorState } from '@/shared/ui/States';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Skeleton } from '@/shared/ui/Skeleton';
import { errorMessage } from '@/shared/types/api';
import type { OnboardingItem } from '@/shared/types/domain';
import { formatDate } from '@/shared/utils/format';
import { cn } from '@/shared/utils/cn';
import { toast } from 'sonner';

/**
 * Onboarding module (§6.9): interactive checklist + S3 document upload
 * (client uploads directly to S3 via pre-signed URL — never through the app
 * server). Completion timestamps come from the backend, always.
 */
export default function OnboardingPage() {
  const checklist = useOnboardingChecklist();
  const complete = useCompleteOnboardingItem();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [targetId, setTargetId] = useState<number | null>(null);

  const items = checklist.data ?? [];
  const done = items.filter((i) => i.completed).length;
  const pct = items.length > 0 ? Math.round((done / items.length) * 100) : 0;

  const toggle = async (item: OnboardingItem) => {
    if (item.completed) return; // completion is one-way in v1
    try {
      await complete.mutateAsync({ id: item.id });
      toast.success('Step completed.');
    } catch (err) {
      toast.error('Could not update step', { description: errorMessage(err) });
    }
  };

  const upload = async (item: OnboardingItem, file: File) => {
    try {
      const presign = await api.post<{ uploadUrl: string; objectKey: string }>(
        `${endpoints.onboarding}/${item.id}/upload-url`,
        { fileName: file.name, contentType: file.type },
      );
      const put = await fetch(presign.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file,
      });
      if (!put.ok) throw new Error('S3 upload failed.');
      await complete.mutateAsync({ id: item.id, objectKey: presign.objectKey });
      toast.success('Document uploaded and step completed.');
    } catch (err) {
      toast.error('Upload failed', { description: errorMessage(err) });
    }
  };

  return (
    <div>
      <PageHeader
        title="Onboarding"
        description="Your checklist from offer to fully set up — documents go straight to S3."
      />

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <div>
            <CardTitle>
              <span className="inline-flex items-center gap-2">
                <ClipboardList className="h-4 w-4 text-primary-ink" aria-hidden />
                Progress
              </span>
            </CardTitle>
            <CardDescription className="mt-0.5">
              {done} of {items.length || '—'} steps complete
            </CardDescription>
          </div>
          <span className="font-mono text-sm text-fg">{pct}%</span>
        </CardHeader>
        <CardContent>
          <div className="h-2 overflow-hidden rounded-full bg-surface-3">
            <div
              className="h-full rounded-full bg-primary transition-all duration-500 ease-snappy"
              style={{ width: `${pct}%` }}
            />
          </div>
        </CardContent>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Checklist</CardTitle>
        </CardHeader>
        <CardContent className="pt-2">
          {checklist.isLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 rounded-lg" />
              ))}
            </div>
          ) : checklist.error ? (
            <ErrorState
              error={checklist.error}
              onRetry={() => void checklist.refetch()}
              title="Couldn't load your checklist"
            />
          ) : items.length === 0 ? (
            <EmptyState
              title="No checklist yet"
              description="Your onboarding checklist appears here once HR sets it up."
            />
          ) : (
            <ul className="divide-y divide-edge/50">
              {items.map((item) => (
                <li key={item.id} className="flex items-center gap-3 py-3">
                  <button
                    type="button"
                    onClick={() => void toggle(item)}
                    disabled={item.completed}
                    aria-label={
                      item.completed ? `${item.title} completed` : `Mark ${item.title} complete`
                    }
                    className={cn(
                      'flex h-6 w-6 shrink-0 items-center justify-center rounded-full transition-colors',
                      item.completed
                        ? 'bg-success/15 text-success'
                        : 'border border-edge text-faint hover:border-primary/60 hover:text-primary-ink',
                    )}
                  >
                    {item.completed ? (
                      <CheckCircle2 className="h-4 w-4" aria-hidden />
                    ) : (
                      <Circle className="h-4 w-4" aria-hidden />
                    )}
                  </button>
                  <div className="min-w-0 flex-1">
                    <p
                      className={cn('text-sm', item.completed ? 'text-muted line-through' : 'text-fg')}
                    >
                      {item.title}
                    </p>
                    {item.completed && item.completedAt && (
                      <p className="font-mono text-[10px] text-faint">
                        Completed {formatDate(item.completedAt)}
                      </p>
                    )}
                    {item.documentKey && (
                      <p className="truncate font-mono text-[10px] text-accent">{item.documentKey}</p>
                    )}
                  </div>
                  {!item.completed && (
                    <>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          setTargetId(item.id);
                          fileInputRef.current?.click();
                        }}
                      >
                        <Upload className="h-3.5 w-3.5" aria-hidden />
                        Upload doc
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => void toggle(item)}>
                        Mark done
                      </Button>
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}

          <input
            ref={fileInputRef}
            type="file"
            className="sr-only"
            tabIndex={-1}
            onChange={(e) => {
              const file = e.target.files?.[0];
              const item = items.find((i) => i.id === targetId);
              if (file && item) void upload(item, file);
              e.target.value = '';
              setTargetId(null);
            }}
          />
        </CardContent>
      </Card>
    </div>
  );
}
