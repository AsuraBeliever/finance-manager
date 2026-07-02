import { format } from "date-fns";

/** Today as a business date (YYYY-MM-DD) in the device's local timezone.
 *  Never use `new Date().toISOString().slice(0, 10)` for this: that is UTC, so
 *  in MX (UTC-6) it rolls over to tomorrow late in the evening. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

/** Short locale-aware date like "5 jul" (year added only when it differs). */
export function formatDayMonth(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString(undefined, opts);
}
