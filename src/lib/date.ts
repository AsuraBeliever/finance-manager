import { format } from "date-fns";

/** Today as a business date (YYYY-MM-DD) in the device's local timezone.
 *  Never use `new Date().toISOString().slice(0, 10)` for this: that is UTC, so
 *  in MX (UTC-6) it rolls over to tomorrow late in the evening. */
export function todayIso(): string {
  return format(new Date(), "yyyy-MM-dd");
}
