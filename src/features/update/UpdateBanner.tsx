import { useRegisterSW } from "virtual:pwa-register/react";
import { RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
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
  const registrationRef = useRef<ServiceWorkerRegistration | null>(null);
  const {
    needRefresh: [needRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      registrationRef.current = registration;
      const check = () => {
        if (navigator.onLine) registration.update();
      };
      setInterval(check, CHECK_INTERVAL_MS);
    },
  });

  const [remoteStale, setRemoteStale] = useState(false);
  const [applying, setApplying] = useState(false);
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

  const apply = async () => {
    // Activating the new worker (download → install → skip-waiting → reload)
    // takes a few seconds on web/PWA with no visible change, so block the
    // button and show an overlay to stop repeat taps from racing the reload.
    if (applying) return;
    setApplying(true);
    // Safety net: if the SW activation never reaches its controllerchange
    // reload (network drop, identical worker…), force a reload so the overlay
    // can't lock the app forever. The happy path reloads well before this.
    window.setTimeout(() => window.location.reload(), 15000);

    // What matters is whether a service worker controls this page, not which
    // detector fired: the version.json poll often flips `remoteStale` before
    // the SW reaches its waiting state, so keying off `needRefresh` here would
    // wrongly take the plain-reload branch on web/iPhone — and a reload there
    // just re-serves the old precached shell, leaving the banner stuck.
    const registration = registrationRef.current;
    if (!navigator.serviceWorker?.controller || !registration) {
      // Desktop shell (WebKitGTK): no controlling SW, so a hard reload fetches
      // the freshly deployed build directly.
      window.location.reload();
      return;
    }

    // Web / installed PWA: activate the new worker so it swaps the precached
    // shell, then reload (updateServiceWorker(true) handles the controllerchange
    // reload). If the new worker hasn't reached `waiting` yet, kick an update
    // and wait for it before skipping it.
    if (registration.waiting) {
      updateServiceWorker(true);
      return;
    }
    registration.addEventListener("updatefound", () => {
      const installing = registration.installing;
      installing?.addEventListener("statechange", () => {
        if (installing.state === "installed" && registration.waiting) {
          updateServiceWorker(true);
        }
      });
    });
    await registration.update();
    if (registration.waiting) updateServiceWorker(true);
  };

  return (
    <>
      <div className="flex shrink-0 items-center justify-center gap-3 bg-accent-dim/15 px-4 py-1.5 text-xs text-accent">
        <span>{es.update.available}</span>
        <button
          onClick={apply}
          disabled={applying}
          className="inline-flex items-center gap-1.5 rounded-md bg-accent-dim px-2.5 py-1 font-medium text-surface transition-colors hover:bg-accent disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-accent-dim"
        >
          <RefreshCw size={13} className={applying ? "animate-spin" : undefined} />
          {applying ? es.update.updating : es.update.action}
        </button>
      </div>

      {applying && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-surface/80 backdrop-blur-sm">
          <div className="flex flex-col items-center gap-4 rounded-2xl border border-border-muted bg-surface-raised px-10 py-8 shadow-card">
            <RefreshCw size={28} className="animate-spin text-accent" />
            <div className="text-center">
              <p className="font-medium text-fg">{es.update.updating}</p>
              <p className="mt-1 text-xs text-fg-subtle">{es.update.updatingHint}</p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
