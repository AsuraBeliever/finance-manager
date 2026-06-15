import { Download, Share } from "lucide-react";
import { Button } from "./Button";
import { usePwaInstall } from "../lib/usePwaInstall";
import { es } from "../i18n/es";

/** Install affordance for the PWA. Chromium shows a real button; iOS Safari
 *  gets the share-sheet instructions; anything else gets a manual hint. Renders
 *  nothing once installed unless `showInstalled` is set. */
export function InstallButton({ showInstalled = false }: { showInstalled?: boolean }) {
  const { installed, canPrompt, ios, promptInstall } = usePwaInstall();

  if (installed) {
    return showInstalled ? <p className="text-sm text-fg-subtle">{es.install.installed}</p> : null;
  }

  if (canPrompt) {
    return (
      <Button variant="ghost" onClick={promptInstall}>
        <span className="flex items-center gap-2">
          <Download size={16} /> {es.install.action}
        </span>
      </Button>
    );
  }

  if (ios) {
    return (
      <p className="flex items-center gap-2 text-sm text-fg-subtle">
        <Share size={15} className="shrink-0" />
        {es.install.iosHint}
      </p>
    );
  }

  return <p className="text-sm text-fg-subtle">{es.install.manualHint}</p>;
}
