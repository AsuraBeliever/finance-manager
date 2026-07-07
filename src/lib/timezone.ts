import { useSyncExternalStore } from "react";

const KEY = "finanzas.timezone";

/** The device's IANA timezone, or Mexico City if it can't be resolved. */
function deviceZone(): string {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (tz) return tz;
  } catch {
    /* ignore */
  }
  return "America/Mexico_City";
}

function detect(): string {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved) return saved;
  } catch {
    /* ignore */
  }
  return deviceZone();
}

let current = detect();
const listeners = new Set<() => void>();

export function getTimezone(): string {
  return current;
}

export function setTimezone(tz: string): void {
  if (tz === current) return;
  current = tz;
  try {
    localStorage.setItem(KEY, tz);
  } catch {
    /* ignore */
  }
  for (const fn of listeners) fn();
}

function subscribe(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Reactive current timezone — components re-render when it changes. */
export function useTimezone(): string {
  return useSyncExternalStore(subscribe, getTimezone, getTimezone);
}

/** The full IANA zone list when the runtime exposes it (modern browsers),
 *  else a curated fallback led by the Mexican zones. Always includes the
 *  device zone and the current selection so neither is unlistable. */
export function listTimezones(): string[] {
  const fallback = [
    "America/Mexico_City",
    "America/Tijuana",
    "America/Cancun",
    "America/Monterrey",
    "America/Hermosillo",
    "America/Ciudad_Juarez",
    "America/Mazatlan",
    "America/New_York",
    "America/Chicago",
    "America/Denver",
    "America/Los_Angeles",
    "America/Bogota",
    "America/Lima",
    "America/Buenos_Aires",
    "Europe/Madrid",
    "UTC",
  ];
  let zones = fallback;
  try {
    const supported = (
      Intl as unknown as { supportedValuesOf?: (k: string) => string[] }
    ).supportedValuesOf?.("timeZone");
    if (supported && supported.length) zones = supported;
  } catch {
    /* ignore */
  }
  return Array.from(new Set([...zones, deviceZone(), current]));
}
