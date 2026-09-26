import { format, formatDistanceToNowStrict, isValid, parseISO } from 'date-fns';

/** Parse an ISO-8601 string safely; returns null when unparseable. */
export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const d = parseISO(value);
  return isValid(d) ? d : null;
}

/** e.g. "12 Oct 2026" */
export function formatDate(value: string | null | undefined): string {
  const d = parseDate(value);
  return d ? format(d, 'dd MMM yyyy') : '—';
}

/** e.g. "09:42" */
export function formatTime(value: string | null | undefined): string {
  const d = parseDate(value);
  return d ? format(d, 'HH:mm') : '—';
}

/** e.g. "3 days ago" — for feeds and notification timestamps. */
export function formatRelative(value: string | null | undefined): string {
  const d = parseDate(value);
  return d ? formatDistanceToNowStrict(d, { addSuffix: true }) : '—';
}

/** Group a plain number (1234567 → 1,234,567). Currency is left unset on
 *  purpose — the backend contract doesn't define one yet. */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);
}

/** Initials for the avatar fallback (derived from the real name — not mock data). */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

/** Deterministic hue from a string — stable avatar colors without a lookup table. */
export function hueFromString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash << 5) - hash + value.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash) % 360;
}

/** Human display for an ISO date-only value (e.g. task deadlines). */
export function isPast(dateValue: string | null | undefined): boolean {
  const d = parseDate(dateValue);
  return d ? d.getTime() < Date.now() : false;
}
