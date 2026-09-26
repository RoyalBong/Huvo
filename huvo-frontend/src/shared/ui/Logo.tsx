import { cn } from '@/shared/utils/cn';

/** Huvo wordmark — restrained, precise (§2). */
export function Logo({ className, showText = true }: { className?: string; showText?: boolean }) {
  return (
    <span className={cn('inline-flex select-none items-center gap-2', className)}>
      <span
        aria-hidden
        className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-accent text-[13px] font-bold text-white shadow-soft"
      >
        H
      </span>
      {showText && (
        <span className="text-[15px] font-semibold tracking-tight text-fg">
          Huvo
        </span>
      )}
    </span>
  );
}
