// User appearance: accent/secondary colors, font pairing, app name and logo.
// Applied via CSS custom properties on <html> (so it overrides the per-theme
// defaults in index.css), persisted to localStorage for an instant, flash-free
// apply and mirrored to the account so it follows the user across devices.
//
// Cross-device sync is last-write-wins by timestamp: every local change stamps
// an `updatedAt` (stored locally and pushed to the account inside an envelope).
// On login we fetch the account copy and adopt it when this device has none or
// when the account's stamp is newer than what we last saved here.
import {
  Coins,
  Gem,
  Heart,
  Landmark,
  Leaf,
  PiggyBank,
  Rocket,
  Sparkles,
  TrendingUp,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { useSyncExternalStore } from "react";
import { getSetting, setSetting } from "./api";

export type FontKey = "default" | "editorial" | "modern" | "classic" | "rounded" | "system";
export type IconKey =
  | "trending-up"
  | "wallet"
  | "piggy-bank"
  | "coins"
  | "landmark"
  | "gem"
  | "sparkles"
  | "rocket"
  | "leaf"
  | "heart";

export interface Appearance {
  accent: string | null; // null = keep the built-in per-theme accent
  gold: string | null; // secondary "pop" color
  surface: string | null; // background tint (mixed over the theme base)
  font: FontKey;
  appName: string;
  icon: IconKey;
  logo: string; // data URL of an uploaded logo image, or "" for the icon
}

export const DEFAULT_APPEARANCE: Appearance = {
  accent: null,
  gold: null,
  surface: null,
  font: "default",
  appName: "Finanzas",
  icon: "trending-up",
  logo: "",
};

// How strongly a chosen background tint is mixed over the theme base. Modest so
// the theme's light/dark dominates and text stays readable.
const TINT_AMOUNT = "28%";

export const ICONS: Record<IconKey, LucideIcon> = {
  "trending-up": TrendingUp,
  wallet: Wallet,
  "piggy-bank": PiggyBank,
  coins: Coins,
  landmark: Landmark,
  gem: Gem,
  sparkles: Sparkles,
  rocket: Rocket,
  leaf: Leaf,
  heart: Heart,
};

interface FontDef {
  display: string | null; // CSS family for headings; null = keep default (Sora)
  sans: string | null; // CSS family for UI text; null = keep default (Hanken)
  href: string | null; // Google Fonts stylesheet to load, or null
}

const SANS_FALLBACK = '"Segoe UI", system-ui, sans-serif';
const DISPLAY_FALLBACK = "system-ui, sans-serif";

export const FONTS: Record<FontKey, FontDef> = {
  default: { display: null, sans: null, href: null },
  editorial: {
    display: `"Fraunces", serif`,
    sans: `"Hanken Grotesk", ${SANS_FALLBACK}`,
    href: "https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&display=swap",
  },
  modern: {
    display: `"Space Grotesk", ${DISPLAY_FALLBACK}`,
    sans: `"Inter", ${SANS_FALLBACK}`,
    href: "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap",
  },
  classic: {
    display: `"Playfair Display", serif`,
    sans: `"Lora", Georgia, serif`,
    href: "https://fonts.googleapis.com/css2?family=Lora:wght@400;500;600&family=Playfair+Display:wght@600;700;800&display=swap",
  },
  rounded: {
    display: `"Baloo 2", ${DISPLAY_FALLBACK}`,
    sans: `"Nunito", ${SANS_FALLBACK}`,
    href: "https://fonts.googleapis.com/css2?family=Baloo+2:wght@600;700;800&family=Nunito:wght@400;500;600;700&display=swap",
  },
  system: { display: DISPLAY_FALLBACK, sans: SANS_FALLBACK, href: null },
};

const KEY = "finanzas.appearance";
const TS_KEY = "finanzas.appearance.updatedAt";

// What we store on the account: the appearance plus the stamp of its last edit,
// so other devices can tell whose copy is newer. Older clients wrote the bare
// Appearance JSON with no stamp; we treat that as the oldest possible (ts 0).
interface AppearanceEnvelope {
  updatedAt: number;
  value: Partial<Appearance>;
}

function read(): Appearance {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...DEFAULT_APPEARANCE, ...(JSON.parse(raw) as Partial<Appearance>) };
  } catch {
    /* ignore */
  }
  return { ...DEFAULT_APPEARANCE };
}

function readLocalTimestamp(): number {
  const raw = localStorage.getItem(TS_KEY);
  const n = raw ? Number(raw) : 0;
  return Number.isFinite(n) ? n : 0;
}

/** Parse the account's stored value, tolerating the legacy stampless format. */
function parseServer(raw: string): { value: Appearance; updatedAt: number } | null {
  try {
    const parsed = JSON.parse(raw) as AppearanceEnvelope | Partial<Appearance>;
    if (parsed && typeof parsed === "object" && "value" in parsed) {
      const env = parsed as AppearanceEnvelope;
      return {
        value: { ...DEFAULT_APPEARANCE, ...env.value },
        updatedAt: typeof env.updatedAt === "number" ? env.updatedAt : 0,
      };
    }
    return { value: { ...DEFAULT_APPEARANCE, ...(parsed as Partial<Appearance>) }, updatedAt: 0 };
  } catch {
    return null;
  }
}

let current: Appearance = read();
const listeners = new Set<() => void>();

/** Apply the appearance to the DOM: CSS variables + font stylesheet. */
export function applyAppearance(a: Appearance): void {
  const root = document.documentElement;
  // Colors — set or clear so the per-theme default returns when null.
  if (a.accent) {
    root.style.setProperty("--c-accent", a.accent);
    root.style.setProperty("--c-accent-dim", `color-mix(in oklab, ${a.accent} 78%, #000)`);
    root.style.setProperty("--c-accent-bright", `color-mix(in oklab, ${a.accent} 72%, #fff)`);
  } else {
    root.style.removeProperty("--c-accent");
    root.style.removeProperty("--c-accent-dim");
    root.style.removeProperty("--c-accent-bright");
  }
  if (a.gold) root.style.setProperty("--c-gold", a.gold);
  else root.style.removeProperty("--c-gold");

  // Background tint — mixed over the theme base via index.css.
  if (a.surface) {
    root.style.setProperty("--bg-tint", a.surface);
    root.style.setProperty("--bg-tint-amt", TINT_AMOUNT);
  } else {
    root.style.removeProperty("--bg-tint");
    root.style.removeProperty("--bg-tint-amt");
  }

  // Fonts.
  const f = FONTS[a.font] ?? FONTS.default;
  if (f.display) root.style.setProperty("--font-display", f.display);
  else root.style.removeProperty("--font-display");
  if (f.sans) root.style.setProperty("--font-sans", f.sans);
  else root.style.removeProperty("--font-sans");
  let link = document.getElementById("appearance-font") as HTMLLinkElement | null;
  if (f.href) {
    if (!link) {
      link = document.createElement("link");
      link.id = "appearance-font";
      link.rel = "stylesheet";
      document.head.appendChild(link);
    }
    if (link.href !== f.href) link.href = f.href;
  } else if (link) {
    link.remove();
  }
}

export function getAppearance(): Appearance {
  return current;
}

let saveTimer: ReturnType<typeof setTimeout> | null = null;

/** Merge a change, apply it, persist locally and (best-effort) to the account. */
export function setAppearance(patch: Partial<Appearance>): void {
  current = { ...current, ...patch };
  applyAppearance(current);
  const updatedAt = Date.now();
  try {
    localStorage.setItem(KEY, JSON.stringify(current));
    localStorage.setItem(TS_KEY, String(updatedAt));
  } catch {
    /* ignore */
  }
  for (const fn of listeners) fn();
  if (saveTimer) clearTimeout(saveTimer);
  const snapshot = JSON.stringify({ updatedAt, value: current } satisfies AppearanceEnvelope);
  saveTimer = setTimeout(() => setSetting("appearance", snapshot).catch(() => {}), 600);
}

export function resetAppearance(): void {
  setAppearance({ ...DEFAULT_APPEARANCE });
}

/**
 * Reconcile this device with the account: adopt the account's appearance when
 * this device has none, or when the account's copy is strictly newer than what
 * we last saved here (so a change on any device propagates to the rest).
 */
export async function hydrateAppearanceFromServer(): Promise<void> {
  try {
    const raw = await getSetting("appearance");
    if (!raw) return; // nothing saved on the account yet
    const server = parseServer(raw);
    if (!server) return;
    const hasLocal = localStorage.getItem(KEY) != null;
    if (hasLocal && server.updatedAt <= readLocalTimestamp()) return; // local is newer
    current = server.value;
    applyAppearance(current);
    localStorage.setItem(KEY, JSON.stringify(current));
    localStorage.setItem(TS_KEY, String(server.updatedAt));
    for (const fn of listeners) fn();
  } catch {
    /* offline or logged out: keep local/default */
  }
}

function subscribe(cb: () => void): () => void {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** Current appearance, reactive (re-renders on change). */
export function useAppearance(): Appearance {
  return useSyncExternalStore(subscribe, getAppearance, getAppearance);
}
