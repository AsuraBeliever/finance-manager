import { useSyncExternalStore } from "react";

export type Locale = "es" | "en";

const KEY = "finanzas.locale";

function detect(): Locale {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "es" || saved === "en") return saved;
  } catch {
    /* ignore */
  }
  // Default to the browser language; anything non-English falls back to Spanish
  // (the app's original locale and primary audience).
  const lang = typeof navigator !== "undefined" ? navigator.language?.toLowerCase() : "";
  return lang?.startsWith("en") ? "en" : "es";
}

let current: Locale = detect();
const listeners = new Set<() => void>();

export function getLocale(): Locale {
  return current;
}

export function setLocale(locale: Locale): void {
  if (locale === current) return;
  current = locale;
  try {
    localStorage.setItem(KEY, locale);
  } catch {
    /* ignore */
  }
  if (typeof document !== "undefined") document.documentElement.lang = locale;
  for (const fn of listeners) fn();
}

function subscribe(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Reactive current locale — components/providers re-render on change. */
export function useLocale(): Locale {
  return useSyncExternalStore(subscribe, getLocale, getLocale);
}
