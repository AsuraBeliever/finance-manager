import { format } from "date-fns";

/** Today as a business date (YYYY-MM-DD) in the device's local timezone.
 *  Never use `new Date().toISOString().slice(0, 10)` for this: that is UTC, so
 *  in MX (UTC-6) it rolls over to tomorrow late in the evening. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}

/** Current wall-clock time as 'HH:MM' (24h) in the given IANA timezone —
 *  the default time for a movement being recorded right now. */
export function nowTime(tz: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: tz,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
}

/** 'HH:MM' (24h wall clock) → locale time like "5:16 p.m." Returns "" for a
 *  null/blank time. The stored value is already local wall clock, so no zone
 *  conversion — we just reformat to 12h. */
export function formatTime(time: string | null | undefined): string {
  if (!time) return "";
  const [h, m] = time.split(":").map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return "";
  const d = new Date();
  d.setHours(h, m, 0, 0);
  return d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

/** A SQLite UTC timestamp ("YYYY-MM-DD HH:MM:SS") → locale time like "5:16 p.m."
 *  in the given timezone. Used to show a time for legacy rows that predate the
 *  editable occurred_time and only have the created_at insert stamp. */
export function formatUtcTime(utc: string | null | undefined, tz: string): string {
  if (!utc) return "";
  const d = new Date(utc.replace(" ", "T") + "Z");
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleTimeString(undefined, {
    timeZone: tz,
    hour: "numeric",
    minute: "2-digit",
  });
}

/** The time to show for a movement: its own edited wall-clock time when set,
 *  else the created_at insert stamp adapted to the chosen timezone. */
export function transactionTime(
  occurredTime: string | null | undefined,
  createdAt: string | null | undefined,
  tz: string,
): string {
  return occurredTime ? formatTime(occurredTime) : formatUtcTime(createdAt, tz);
}

/** Short locale-aware date like "5 jul" (year added only when it differs). */
export function formatDayMonth(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString(undefined, opts);
}
