import { useRegisterSW } from "virtual:pwa-register/react";
import { RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { es } from "../../i18n/es";

// Re-check for a new deployment hourly and whenever the window regains focus,
// so the desktop shell (which stays open for days) notices updates without a
// manual reload.
const CHECK_INTERVAL_MS = 60 * 60 * 1000;

/** Read the build id of the currently deployed app. Cache-busted and no-store
 *  so neither the WebKitGTK HTTP cache nor the Cloudflare edge serves a stale
 *  copy; version.json is also excluded from the SW precache (see vite.config). */
async function deployedBuildId(): Promise<string | null> {
  try {
    const res = await fetch(`/version.json?_=${Date.now()}`, { cache: "no-store" });
    if (!res.ok) return null;
    const data = (await res.json()) as { buildId?: string };
    return data.buildId ?? null;
  } catch {
    return null;
  }
}

/** Shows an "Actualizar" bar when a new version has been deployed. Two
 *  independent detectors feed the same banner:
 *
 *  1. Service worker (web + installed iPhone PWA): when a new worker reaches
 *     the waiting state, `needRefresh` flips; tapping activates it and reloads.
 *  2. Build-id poll (desktop): the Tauri/WebKitGTK webview doesn't fire SW
 *     update events reliably, so we also compare the build id compiled into
 *     this bundle against the deployed version.json and, when they differ,
 *     offer a hard reload. Runs independently of SW registration. */
export function UpdateBanner() {
  const {
    needRefresh: [needRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      const check = () => {
        if (navigator.onLine) registration.update();
      };
      setInterval(check, CHECK_INTERVAL_MS);
    },
  });

  const [remoteStale, setRemoteStale] = useState(false);
  useEffect(() => {
    let alive = true;
    const check = async () => {
      if (remoteStale || !navigator.onLine) return;
      const deployed = await deployedBuildId();
      if (alive && deployed && deployed !== __BUILD_ID__) setRemoteStale(true);
    };
    check();
    const interval = setInterval(check, CHECK_INTERVAL_MS);
    // `focus` is the reliable "user came back to the window" signal in the
    // desktop shell; visibilitychange covers tab/PWA backgrounding on web/iOS.
    const onVisible = () => document.visibilityState === "visible" && check();
    window.addEventListener("focus", check);
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", check);
    return () => {
      alive = false;
      clearInterval(interval);
      window.removeEventListener("focus", check);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", check);
    };
  }, [remoteStale]);

  if (!needRefresh && !remoteStale) return null;

  const apply = () => {
    // A waiting SW (web/iPhone) must be activated to swap the cached shell; a
    // plain reload there would re-serve the old precached assets. The desktop
    // shell has no controlling SW, so a hard reload fetches the new build.
    if (needRefresh) updateServiceWorker(true);
    else window.location.reload();
  };

  return (
    <div className="flex shrink-0 items-center justify-center gap-3 bg-accent-dim/15 px-4 py-1.5 text-xs text-accent">
      <span>{es.update.available}</span>
      <button
        onClick={apply}
        className="inline-flex items-center gap-1.5 rounded-md bg-accent-dim px-2.5 py-1 font-medium text-surface transition-colors hover:bg-accent"
      >
        <RefreshCw size={13} />
        {es.update.action}
      </button>
    </div>
  );
}
