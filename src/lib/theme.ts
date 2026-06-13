// Theme state. The pre-paint script in index.html applies the theme before
// React mounts (no flash); this module keeps it in sync afterwards, persists
// the preference to localStorage, and (best-effort) mirrors it to the account
// so it follows the user across devices.
import { useSyncExternalStore } from "react";
import { getSetting, setSetting } from "./api";

export type ThemePref = "light" | "dark" | "system";
type Resolved = "light" | "dark";

const KEY = "finanzas.theme";
const THEME_COLOR = { dark: "#141210", light: "#f4f4f8" } as const;
const listeners = new Set<() => void>();
const mql = window.matchMedia("(prefers-color-scheme: dark)");

function isPref(v: string | null): v is ThemePref {
  return v === "light" || v === "dark" || v === "system";
}

export function getThemePref(): ThemePref {
  const v = localStorage.getItem(KEY);
  return isPref(v) ? v : "system";
}

export function resolveTheme(pref: ThemePref = getThemePref()): Resolved {
  return pref === "dark" || (pref === "system" && mql.matches) ? "dark" : "light";
}

function apply(pref: ThemePref) {
  const r = resolveTheme(pref);
  document.documentElement.dataset.theme = r;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute("content", THEME_COLOR[r]);
  listeners.forEach((l) => l());
}

/** Change the preference: persist locally, repaint, and push to the account. */
export function setThemePref(pref: ThemePref) {
  localStorage.setItem(KEY, pref);
  apply(pref);
  // Best-effort: ignore failures (offline or logged out — local pref still wins).
  setSetting("theme", pref).catch(() => {});
}

/** On login, adopt the account's saved theme if it differs from the local one. */
export async function hydrateThemeFromServer(): Promise<void> {
  try {
    const remote = await getSetting("theme");
    if (isPref(remote) && remote !== getThemePref()) {
      localStorage.setItem(KEY, remote);
      apply(remote);
    }
  } catch {
    /* offline or unauthenticated: keep the local preference */
  }
}

// Repaint when the OS theme flips while following the system preference.
mql.addEventListener("change", () => {
  if (getThemePref() === "system") apply("system");
});

function subscribe(cb: () => void) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** Current preference + setter (re-renders on change). */
export function useThemePref(): [ThemePref, (p: ThemePref) => void] {
  const pref = useSyncExternalStore<ThemePref>(
    subscribe,
    getThemePref,
    () => "system",
  );
  return [pref, setThemePref];
}

/** The resolved 'light' | 'dark' actually in effect (for theme-aware charts). */
export function useResolvedTheme(): Resolved {
  return useSyncExternalStore<Resolved>(
    subscribe,
    () => resolveTheme(),
    () => "dark",
  );
}
