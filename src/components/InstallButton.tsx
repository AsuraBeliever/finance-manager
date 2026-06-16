import { Download, Share } from "lucide-react";
import { Button } from "./Button";
import { usePwaInstall } from "../lib/usePwaInstall";
import { useDesktopDownload } from "../lib/useDesktopDownload";
import { es } from "../i18n/es";

/** Install affordance. On a desktop PC (Windows/Linux) it offers the native app
 *  download — that's the real desktop product, and Firefox can't install PWAs.
 *  Mobile keeps the PWA flow: Android gets Chromium's prompt, iOS the share-sheet
 *  hint. Renders nothing once installed unless `showInstalled` is set. */
export function InstallButton({ showInstalled = false }: { showInstalled?: boolean }) {
  const { installed, desktop, canPrompt, ios, promptInstall } = usePwaInstall();
  const download = useDesktopDownload();

  // The desktop shell IS the installed app; never offer to install there.
  if (desktop) {
    return showInstalled ? <p className="text-sm text-fg-subtle">{es.install.desktopApp}</p> : null;
  }

  if (installed) {
    return showInstalled ? <p className="text-sm text-fg-subtle">{es.install.installed}</p> : null;
  }

  // Desktop PC: download the native app instead of the PWA.
  if (download.os) {
    return (
      <div className="flex flex-col items-center gap-1.5">
        <a
          href={download.href}
          className="rounded-lg bg-accent-dim px-4 py-2 text-sm font-medium text-white shadow-[0_1px_0_rgba(255,255,255,0.12)_inset,0_8px_18px_-10px_rgba(22,164,122,0.8)] transition-all duration-150 outline-none hover:bg-accent active:translate-y-px focus-visible:ring-2 focus-visible:ring-accent/50"
        >
          <span className="flex items-center gap-2">
            <Download size={16} />
            {download.os === "windows" ? es.install.downloadWindows : es.install.downloadLinux}
          </span>
        </a>
        {download.os === "linux" && (
          <a
            href={download.releasesPage}
            target="_blank"
            rel="noreferrer"
            className="text-xs text-fg-subtle underline-offset-2 hover:underline"
          >
            {es.install.otherFormats}
          </a>
        )}
      </div>
    );
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

  // Other browsers without a prompt: a hint only in the detailed (Settings)
  // context; nothing floating on the login screen.
  return showInstalled ? <p className="text-sm text-fg-subtle">{es.install.manualHint}</p> : null;
}
