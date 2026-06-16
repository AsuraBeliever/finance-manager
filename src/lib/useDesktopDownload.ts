import { useEffect, useState } from "react";

// On a desktop PC the real product is the native app (the Tauri shell), not the
// PWA — Firefox can't install PWAs at all, and even on Chromium a proper .exe /
// .AppImage with its own icon is what users expect. The installers are built and
// published to GitHub Releases by .github/workflows/release.yml on every tag, so
// here we just resolve the latest release's asset for the visitor's OS.

const REPO = "AsuraBeliever/finance-manager";
const RELEASES_PAGE = `https://github.com/${REPO}/releases/latest`;
const LATEST_API = `https://api.github.com/repos/${REPO}/releases/latest`;

/** Desktop OSes we ship native installers for. */
export type DesktopOs = "windows" | "linux";

/** Coarse OS family from the UA. Mobile (iOS/Android) and macOS return null:
 *  those keep the PWA flow, since we don't build mac installers in CI. */
export function detectDesktopOs(): DesktopOs | null {
  if (typeof navigator === "undefined") return null;
  const ua = navigator.userAgent;
  if (/Windows/.test(ua)) return "windows";
  if (/Linux/.test(ua) && !/Android/.test(ua)) return "linux";
  return null;
}

interface GhAsset {
  name: string;
  browser_download_url: string;
}

/** Pick the friendliest installer for the OS from a release's assets. */
function pickAsset(os: DesktopOs, assets: GhAsset[]): string | null {
  const find = (re: RegExp) =>
    assets.find((a) => re.test(a.name))?.browser_download_url ?? null;
  if (os === "windows") return find(/-setup\.exe$/i) ?? find(/\.msi$/i) ?? find(/\.exe$/i);
  // .AppImage runs on any distro with no install step; .deb/.rpm live on the
  // release page (linked separately) for those who want menu integration.
  return find(/\.AppImage$/i);
}

/** Resolves a one-click download for the current desktop OS, falling back to the
 *  Releases page if the GitHub API is unavailable or the asset isn't found. */
export function useDesktopDownload() {
  const os = detectDesktopOs();
  const [directUrl, setDirectUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!os) return;
    let alive = true;
    fetch(LATEST_API, { headers: { Accept: "application/vnd.github+json" } })
      .then((r) => (r.ok ? r.json() : null))
      .then((data: { assets?: GhAsset[] } | null) => {
        if (alive && data?.assets) setDirectUrl(pickAsset(os, data.assets));
      })
      .catch(() => {
        /* keep the releases-page fallback */
      });
    return () => {
      alive = false;
    };
  }, [os]);

  return {
    os,
    /** One-click installer when resolved, else the Releases page. */
    href: directUrl ?? RELEASES_PAGE,
    releasesPage: RELEASES_PAGE,
  };
}
