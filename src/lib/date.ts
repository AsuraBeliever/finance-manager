import { format } from "date-fns";
import type { Clock } from "./timeFormat";

/** Intl options for a time in the chosen clock format. */
function timeOpts(clock: Clock): Intl.DateTimeFormatOptions {
  return clock === "24"
    ? { hour: "2-digit", minute: "2-digit", hour12: false }
    : { hour: "numeric", minute: "2-digit", hour12: true };
}

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

/** 'HH:MM' (24h wall clock) → locale time in the chosen clock format. Returns
 *  "" for a null/blank time. The stored value is already local wall clock, so
 *  no zone conversion — we just reformat. */
export function formatTime(time: string | null | undefined, clock: Clock): string {
  if (!time) return "";
  const [h, m] = time.split(":").map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return "";
  const d = new Date();
  d.setHours(h, m, 0, 0);
  return d.toLocaleTimeString(undefined, timeOpts(clock));
}

/** A SQLite UTC timestamp ("YYYY-MM-DD HH:MM:SS") → locale time in the given
 *  timezone and clock format. Used to show a time for legacy rows that predate
 *  the editable occurred_time and only have the created_at insert stamp. */
export function formatUtcTime(
  utc: string | null | undefined,
  tz: string,
  clock: Clock,
): string {
  if (!utc) return "";
  const d = new Date(utc.replace(" ", "T") + "Z");
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleTimeString(undefined, { timeZone: tz, ...timeOpts(clock) });
}

/** The time to show for a movement: its own edited wall-clock time when set,
 *  else the created_at insert stamp adapted to the chosen timezone. */
export function transactionTime(
  occurredTime: string | null | undefined,
  createdAt: string | null | undefined,
  tz: string,
  clock: Clock,
): string {
  return occurredTime
    ? formatTime(occurredTime, clock)
    : formatUtcTime(createdAt, tz, clock);
}

/** The 'HH:MM' (24h) value to seed the time picker when editing a movement:
 *  its own edited time, else the created_at insert stamp converted to the
 *  chosen timezone, so editing a legacy row starts from the time shown in the
 *  list instead of blank. "" only when neither is available. */
export function timeInputValue(
  occurredTime: string | null | undefined,
  createdAt: string | null | undefined,
  tz: string,
): string {
  if (occurredTime) return occurredTime;
  if (!createdAt) return "";
  const d = new Date(createdAt.replace(" ", "T") + "Z");
  if (Number.isNaN(d.getTime())) return "";
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: tz,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(d);
}

/** Short locale-aware date like "5 jul" (year added only when it differs). */
export function formatDayMonth(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString(undefined, opts);
}
