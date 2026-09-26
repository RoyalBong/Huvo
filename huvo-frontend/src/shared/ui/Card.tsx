import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/shared/utils/cn';

/**
 * Surface container. `plain` renders a solid surface — used where glass would
 * hurt the content (e.g. Payroll, §6.6: "sensitive financial data doesn't get
 * glassmorphism"). Default is the restrained glass reserved for cards (§2).
 */
export function Card({ className, plain, ...props }: HTMLAttributes<HTMLDivElement> & { plain?: boolean }) {
  return (
    <div
      className={cn(
        'rounded-card border shadow-soft',
        plain ? 'border-edge bg-surface-1' : 'glass border-edge/80',
        className,
      )}
      {...props}
    />
  );
}

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1 p-5 pb-3', className)} {...props} />;
}

export function CardTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn('text-sm font-semibold tracking-tight text-fg', className)} {...props} />;
}

export function CardDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-xs text-muted', className)} {...props} />;
}

export function CardContent({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-5 pt-2', className)} {...props} />;
}

export function CardFooter({ className, children, ...props }: HTMLAttributes<HTMLDivElement> & { children?: ReactNode }) {
  return (
    <div className={cn('flex items-center gap-2 border-t border-edge/60 p-4', className)} {...props}>
      {children}
    </div>
  );
}
