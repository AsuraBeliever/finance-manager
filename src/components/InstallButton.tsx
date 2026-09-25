import { Download, Share } from "lucide-react";
import { Button } from "./Button";
import { usePwaInstall } from "../lib/usePwaInstall";
import { useAppDownload } from "../lib/useAppDownload";
import { es } from "../i18n/es";

type InstallOffer = "download" | "prompt" | "ios" | "manual" | null;

/** What, if anything, this device should be offered. Where there's a native
 *  build (Windows, Linux, Android) that's the product: on Android the APK is
 *  offered even from an installed PWA, since the PWA is not the app. Inside the
 *  desktop shell, or once installed, there's nothing to offer — and nothing is
 *  said, because "already installed" only tells the user what they know. */
export function useInstallOffer() {
  const pwa = usePwaInstall();
  const download = useAppDownload();
  let offer: InstallOffer = null;
  if (pwa.desktop) offer = null;
  else if (download.os === "android") offer = "download";
  else if (pwa.installed) offer = null;
  else if (download.os) offer = "download";
  else if (pwa.canPrompt) offer = "prompt";
  else if (pwa.ios) offer = "ios";
  else offer = "manual";
  return { offer, pwa, download };
}

const downloadClass =
  "rounded-lg bg-accent-dim px-4 py-2 text-sm font-medium text-white shadow-[0_1px_0_rgba(255,255,255,0.12)_inset,0_8px_18px_-10px_rgba(22,164,122,0.8)] transition-all duration-150 outline-none hover:bg-accent active:translate-y-px focus-visible:ring-2 focus-visible:ring-accent/50";

/** Install affordance for the login screen; renders nothing when there's
 *  nothing to offer. */
export function InstallButton() {
  return <InstallOfferView install={useInstallOffer()} />;
}

/** Native download where one exists; otherwise the PWA flow (Chromium's
 *  prompt, the iOS share-sheet hint). The Android permission note and the
 *  generic "use your browser menu" hint only appear in the detailed (Settings)
 *  context. Takes the state from the caller so Settings can also decide
 *  whether to show its section at all. */
export function InstallOfferView({
  install: { offer, pwa, download },
  detailed = false,
}: {
  install: ReturnType<typeof useInstallOffer>;
  detailed?: boolean;
}) {
  if (offer === "download") {
    const label =
      download.os === "android"
        ? es.install.downloadAndroid
        : download.os === "windows"
          ? es.install.downloadWindows
          : es.install.downloadLinux;
    return (
      <div className="flex flex-col items-center gap-1.5">
        <a href={download.href} className={downloadClass}>
          <span className="flex items-center gap-2">
            <Download size={16} />
            {label}
          </span>
        </a>
        {download.os === "android" && detailed && (
          <p className="text-center text-xs text-fg-subtle">{es.install.androidHint}</p>
        )}
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

  if (offer === "prompt") {
    return (
      <Button variant="ghost" onClick={pwa.promptInstall}>
        <span className="flex items-center gap-2">
          <Download size={16} /> {es.install.action}
        </span>
      </Button>
    );
  }

  if (offer === "ios") {
    return (
      <p className="flex items-center gap-2 text-sm text-fg-subtle">
        <Share size={15} className="shrink-0" />
        {es.install.iosHint}
      </p>
    );
  }

  if (offer === "manual" && detailed) {
    return <p className="text-sm text-fg-subtle">{es.install.manualHint}</p>;
  }

  return null;
}
