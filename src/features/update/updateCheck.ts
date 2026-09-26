// "Check for updates" in Settings runs the same detection the update bar does,
// on demand. The bar registers its checker here; the button calls it and, when
// it finds a new version, the bar is already showing by the time it returns.

/** true = a new version is available (the bar is up), false = up to date,
 *  null = could not tell (offline, request failed). */
export type UpdateCheck = () => Promise<boolean | null>;

let checker: UpdateCheck | null = null;

export function registerUpdateChecker(fn: UpdateCheck): () => void {
  checker = fn;
  return () => {
    if (checker === fn) checker = null;
  };
}

/** Compare the build id compiled into this bundle with the deployed one. */
export async function deployedIsNewer(): Promise<boolean | null> {
  try {
    const res = await fetch(`/version.json?_=${Date.now()}`, { cache: "no-store" });
    if (!res.ok) return null;
    const data = (await res.json()) as { buildId?: string };
    return data.buildId ? data.buildId !== __BUILD_ID__ : null;
  } catch {
    return null;
  }
}

export function checkForUpdates(): Promise<boolean | null> {
  // No bar registered (localhost dev serves the fresh build on every reload):
  // still answer honestly from version.json.
  return checker ? checker() : deployedIsNewer();
}
