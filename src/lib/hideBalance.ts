// "Hide balance" / privacy mode (the eye toggle). Local-only — it's a
// per-device privacy convenience, not account state. When on, every money
// figure across the app is masked so you can show the screen to someone without
// revealing balances, income or expenses (charts keep their shape).
import { useSyncExternalStore } from "react";
import { formatCents } from "./money";

const KEY = "finanzas.hideBalance";
const listeners = new Set<() => void>();

function get(): boolean {
  return localStorage.getItem(KEY) === "1";
}

export function toggleHideBalance() {
  localStorage.setItem(KEY, get() ? "0" : "1");
  listeners.forEach((l) => l());
}

function subscribe(cb: () => void) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** [hidden, toggle]. Components mask money with MASK when hidden. */
export function useHideBalance(): [boolean, () => void] {
  const hidden = useSyncExternalStore(subscribe, get, () => false);
  return [hidden, toggleHideBalance];
}

export const MASK = "••••••";

/** A money formatter that returns {@link MASK} while privacy mode is on and the
 *  real amount otherwise. Prefer this over `formatCents` for anything that
 *  DISPLAYS a stored figure; keep `formatCents` for live previews of what the
 *  user is actively typing into a form. Subscribing re-renders on toggle. */
export function useMoney(): (cents: number, currencyCode?: string) => string {
  const [hidden] = useHideBalance();
  return (cents: number, currencyCode = "MXN") =>
    hidden ? MASK : formatCents(cents, currencyCode);
}
