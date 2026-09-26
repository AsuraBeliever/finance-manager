// Tells the account sync (./preferences.ts) that a device preference changed.
// Kept import-free on purpose: the stores that emit it (i18n, clock, timezone,
// theme, changelog) are also what preferences.ts reads and writes, and a direct
// import either way would be a cycle.
const listeners = new Set<() => void>();

export function onPrefChange(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function emitPrefChange(): void {
  for (const fn of listeners) fn();
}
