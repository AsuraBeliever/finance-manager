import { format } from "date-fns";

/** Today as a business date (YYYY-MM-DD) in the device's local timezone.
 *  Never use `new Date().toISOString().slice(0, 10)` for this: that is UTC, so
 *  in MX (UTC-6) it rolls over to tomorrow late in the evening. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

/** Current wall-clock time as 'HH:MM' (24h) in the device's local timezone —
 *  the default time for a movement being recorded right now. */
export function nowTime(): string {
  return format(new Date(), "HH:mm");
}

/** 'HH:MM' (24h) → locale time like "5:16 p.m." Returns "" for a null/blank
 *  time so untimed rows render nothing. */
export function formatTime(time: string | null | undefined): string {
  if (!time) return "";
  const [h, m] = time.split(":").map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return "";
  const d = new Date();
  d.setHours(h, m, 0, 0);
  return d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

/** Short locale-aware date like "5 jul" (year added only when it differs). */
export function formatDayMonth(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString(undefined, opts);
}
