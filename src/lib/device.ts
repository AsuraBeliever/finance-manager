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
