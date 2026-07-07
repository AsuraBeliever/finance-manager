import { useSyncExternalStore } from "react";

/** "12" = 5:16 p.m. · "24" = 17:16. */
export type Clock = "12" | "24";

const KEY = "finanzas.clock";

function detect(): Clock {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "12" || saved === "24") return saved;
  } catch {
    /* ignore */
  }
  // Follow the device's convention: if it formats noon with AM/PM, it's 12h.
  try {
    const parts = new Intl.DateTimeFormat(undefined, { hour: "numeric" }).formatToParts(
      new Date(2020, 0, 1, 13),
    );
    return parts.some((p) => p.type === "dayPeriod") ? "12" : "24";
  } catch {
    return "24";
  }
}

let current: Clock = detect();
const listeners = new Set<() => void>();

export function getClock(): Clock {
  return current;
}

export function setClock(clock: Clock): void {
  if (clock === current) return;
  current = clock;
  try {
    localStorage.setItem(KEY, clock);
  } catch {
    /* ignore */
  }
  for (const fn of listeners) fn();
}

function subscribe(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Reactive current clock format — components re-render when it changes. */
export function useClock(): Clock {
  return useSyncExternalStore(subscribe, getClock, getClock);
}
