import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string | null;
  hint?: string;
  /** Rendered inside the field, right-aligned (e.g. show/hide password). */
  trailing?: ReactNode;
}

/** Text input with label/hint/error wiring — AA-contrast in both themes. */
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, trailing, id, type, ...props }, ref) => {
    const autoId = useId();
    const inputId = id ?? autoId;
    const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={inputId} className="text-sm font-medium text-fg">
            {label}
          </label>
        )}
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            type={type}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            className={cn(
              'h-9 w-full rounded-lg border border-edge bg-surface-2 px-3 text-sm text-fg placeholder:text-faint transition-colors duration-150',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:border-primary/60',
              error && 'border-danger/70 focus-visible:ring-danger/50',
              trailing && 'pr-10',
              'disabled:cursor-not-allowed disabled:opacity-50',
              className,
            )}
            {...props}
          />
          {trailing && (
            <div className="absolute right-1.5 top-1/2 -translate-y-1/2">{trailing}</div>
          )}
        </div>
        {error && (
          <p id={`${inputId}-error`} role="alert" className="text-xs text-danger">
            {error}
          </p>
        )}
        {!error && hint && (
          <p id={`${inputId}-hint`} className="text-xs text-muted">
            {hint}
          </p>
        )}
      </div>
    );
  },
);
Input.displayName = 'Input';

