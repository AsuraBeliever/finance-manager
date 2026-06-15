// Minimal User-Agent parsing for the devices list — a friendly label and an
// icon hint, nothing more. No dependency needed at this fidelity.

import { es } from "../i18n/es";

export function deviceLabel(ua: string | null): string {
  if (!ua) return es.account.unknownDevice;
  const os = /iPhone/.test(ua)
    ? "iPhone"
    : /iPad/.test(ua)
      ? "iPad"
      : /Android/.test(ua)
        ? "Android"
        : /Windows/.test(ua)
          ? "Windows"
          : /Macintosh|Mac OS X/.test(ua)
            ? "Mac"
            : /Linux/.test(ua)
              ? "Linux"
              : null;
  // order matters: Chrome's UA contains "Safari", Edge's contains "Chrome"
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\//.test(ua)
      ? "Opera"
      : /Chrome\//.test(ua)
        ? "Chrome"
        : /Firefox\//.test(ua)
          ? "Firefox"
          : /Safari\//.test(ua)
            ? "Safari"
            : null;
  // WebKitGTK (the Tauri desktop shell) reports Linux + Safari
  if (os === "Linux" && browser === "Safari") {
    return `Linux · ${es.account.desktopApp}`;
  }
  if (os && browser) return `${os} · ${browser}`;
  return os ?? browser ?? es.account.unknownDevice;
}

export function isMobileDevice(ua: string | null): boolean {
  return !!ua && /iPhone|iPad|Android|Mobile/.test(ua);
}

/** True when running inside the Tauri desktop shell (which loads the deployed
 *  URL in a WebKitGTK webview) rather than a normal browser. Prefer the Tauri
 *  globals; fall back to the UA signature (Linux + WebKit Safari, not Chrome),
 *  which is how the desktop shell reports itself. Used to suppress the "install
 *  app" affordance there — it's already the installed desktop app. */
export function isDesktopShell(): boolean {
  if (typeof window === "undefined") return false;
  const w = window as unknown as Record<string, unknown>;
  if ("__TAURI__" in w || "__TAURI_INTERNALS__" in w || "isTauri" in w) return true;
  const ua = navigator.userAgent;
  return /Linux/.test(ua) && /Safari/.test(ua) && !/Chrome|Chromium|Android/.test(ua);
}
