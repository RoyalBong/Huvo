import { cn } from '@/shared/utils/cn';
import { hueFromString, initials } from '@/shared/utils/format';

/**
 * Avatar with deterministic initials fallback — color is derived from the
 * person's real name, never from a static lookup table of fake people.
 */
export function Avatar({
  name,
  className,
  size = 'md',
}: {
  name: string;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}) {
  const hue = hueFromString(name);
  const sizeClasses =
    size === 'sm' ? 'h-7 w-7 text-[10px]' : size === 'lg' ? 'h-12 w-12 text-base' : 'h-9 w-9 text-xs';

  return (
    <span
      aria-hidden
      title={name}
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold text-white',
        sizeClasses,
        className,
      )}
      style={{
        background: `linear-gradient(135deg, hsl(${hue} 55% 42%), hsl(${(hue + 40) % 360} 60% 55%))`,
      }}
    >
      {initials(name)}
    </span>
  );
}
