// "Hide balance" preference (the eye toggle on the dashboard). Local-only —
// it's a per-device privacy convenience, not account state.
import { useSyncExternalStore } from "react";

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
