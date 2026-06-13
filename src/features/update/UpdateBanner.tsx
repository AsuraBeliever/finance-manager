import { useRegisterSW } from "virtual:pwa-register/react";
import { RefreshCw } from "lucide-react";
import { es } from "../../i18n/es";

// Re-check for a new deployment hourly and whenever the app regains focus, so
// the desktop shell (which stays open for days) notices updates without a
// manual reload. The same code drives web and the installed iPhone PWA.
const CHECK_INTERVAL_MS = 60 * 60 * 1000;

/** Shows an "Actualizar" bar when a new version has been deployed. The user
 *  taps it; `updateServiceWorker(true)` activates the new worker and reloads. */
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
      document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") check();
      });
    },
  });

  if (!needRefresh) return null;

  return (
    <div className="flex shrink-0 items-center justify-center gap-3 bg-accent-dim/15 px-4 py-1.5 text-xs text-accent">
      <span>{es.update.available}</span>
      <button
        onClick={() => updateServiceWorker(true)}
        className="inline-flex items-center gap-1.5 rounded-md bg-accent-dim px-2.5 py-1 font-medium text-surface transition-colors hover:bg-accent"
      >
        <RefreshCw size={13} />
        {es.update.action}
      </button>
    </div>
  );
}
