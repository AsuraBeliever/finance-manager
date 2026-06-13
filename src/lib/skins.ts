// Wallet card "skins". Original gradient art (no bank logos/trademarks); the
// crystalline glass sheen is added by <WalletCard>. A skin value stored on a
// wallet is one of:
//   - a catalog id ("oro", "azul", …)
//   - "grad:<hex>"            custom single-color gradient
//   - "grad:<from>,<to>,<angle>"  explicit gradient (legacy)
//   - "img:<data-url-or-url>"      imported image (the user's own)
// NULL falls back to a default derived from the wallet color.

export type SkinGroup = "banco" | "nivel" | "efectivo" | "glass";

/** Decorative motif drawn on the card. Card skins get a chip; cash skins get
 *  a stylized money illustration instead. */
export type SkinArt = "chip" | "banknote" | "wallet" | "coins" | "none";

export interface Skin {
  id: string;
  label: string;
  group: SkinGroup;
  background: string;
  /** Representative solid color (used for the wallet's chart dot). */
  accent: string;
  /** Text/foreground color that reads on this background. */
  fg: string;
  /** Motif; defaults to "chip" when omitted. */
  art?: SkinArt;
}

export const SKINS: Skin[] = [
  // ── color tones (bank-inspired by hue; name your wallet e.g. "BBVA Oro") ──
  { id: "azul", label: "Azul", group: "banco", accent: "#1565d8", fg: "#eaf2ff",
    background: "linear-gradient(135deg,#0a3d91 0%,#1565d8 55%,#0a3d91 100%)" },
  { id: "marino", label: "Azul marino", group: "banco", accent: "#16357e", fg: "#e8eefc",
    background: "linear-gradient(135deg,#0b1f4d 0%,#16357e 100%)" },
  { id: "turquesa", label: "Turquesa", group: "banco", accent: "#22b8cf", fg: "#e9fbff",
    background: "linear-gradient(135deg,#0e7490 0%,#22b8cf 100%)" },
  { id: "rojo", label: "Rojo", group: "banco", accent: "#e1232b", fg: "#fff0f0",
    background: "linear-gradient(135deg,#9e1414 0%,#e1232b 60%,#9e1414 100%)" },
  { id: "vino", label: "Vino", group: "banco", accent: "#9c1f3d", fg: "#ffeef2",
    background: "linear-gradient(135deg,#5b0b22 0%,#9c1f3d 100%)" },
  { id: "morado", label: "Morado", group: "banco", accent: "#9333ea", fg: "#f6effe",
    background: "linear-gradient(135deg,#5b1ea6 0%,#9333ea 60%,#6d28d9 100%)" },
  { id: "verde", label: "Verde", group: "banco", accent: "#10b981", fg: "#eafff4",
    background: "linear-gradient(135deg,#065f46 0%,#10b981 100%)" },

  // ── tiers ──
  { id: "oro", label: "Oro", group: "nivel", accent: "#caa12f", fg: "#3a2c06",
    background: "linear-gradient(135deg,#b8860b 0%,#f5d479 45%,#caa12f 100%)" },
  { id: "platino", label: "Platino", group: "nivel", accent: "#aeb6c0", fg: "#23262e",
    background: "linear-gradient(135deg,#9aa3ad 0%,#e6ebf0 50%,#aeb6c0 100%)" },
  { id: "black", label: "Black", group: "nivel", accent: "#3a3a48", fg: "#ece9f5",
    background: "linear-gradient(135deg,#0a0a0f 0%,#23232e 55%,#0a0a0f 100%)" },
  { id: "infinite", label: "Infinite", group: "nivel", accent: "#1e2a78", fg: "#e7eeff",
    background: "linear-gradient(135deg,#0b1026 0%,#1e2a78 50%,#0b1026 100%)" },

  // ── efectivo (cash) — money illustrations, no chip ──
  { id: "efectivo", label: "Billetes", group: "efectivo", accent: "#2f9e63", fg: "#eafff0",
    art: "banknote",
    background: "linear-gradient(135deg,#1b5e3a 0%,#2f9e63 50%,#1b5e3a 100%)" },
  { id: "cuero", label: "Cartera", group: "efectivo", accent: "#8a5a2b", fg: "#fbeede",
    art: "wallet",
    background: "linear-gradient(135deg,#5a3413 0%,#8a5a2b 55%,#4a2a0f 100%)" },
  { id: "monedas", label: "Monedas", group: "efectivo", accent: "#c08a2e", fg: "#fff6e6",
    art: "coins",
    background: "linear-gradient(135deg,#7a5210 0%,#d9a93a 50%,#6b450c 100%)" },

  // ── neon glass (matches the app) ──
  { id: "neon", label: "Neón", group: "glass", accent: "#a855f7", fg: "#f4f0ff",
    background: "linear-gradient(135deg,#7c3aed 0%,#a855f7 45%,#22d3ee 110%)" },
  { id: "holo", label: "Holográfico", group: "glass", accent: "#c4b5fd", fg: "#1a1430",
    background: "linear-gradient(135deg,#a5f3fc 0%,#c4b5fd 35%,#fbcfe8 70%,#fde68a 100%)" },
  { id: "noche", label: "Noche", group: "glass", accent: "#2a2350", fg: "#e8e6f4",
    background: "linear-gradient(135deg,#141228 0%,#2a2350 100%)" },
];

const BY_ID = new Map(SKINS.map((s) => [s.id, s]));

export interface ResolvedSkin {
  background: string;
  fg: string;
}

/** Build a glossy gradient from a single base color. */
function gradientFrom(c: string): string {
  return `linear-gradient(135deg, ${c} 0%, color-mix(in oklab, ${c} 52%, #000) 100%)`;
}

/** Resolve a stored skin value (catalog id / grad / img / null) to CSS. */
export function resolveSkin(
  skin: string | null | undefined,
  color?: string | null,
): ResolvedSkin {
  if (skin) {
    if (skin.startsWith("img:")) {
      const url = skin.slice(4);
      return {
        background: `center / cover no-repeat url("${url.replace(/"/g, '\\"')}")`,
        fg: "#ffffff",
      };
    }
    if (skin.startsWith("grad:")) {
      const parts = skin.slice(5).split(",");
      if (parts.length >= 3) {
        const [from, to, angle] = parts;
        return { background: `linear-gradient(${angle}deg, ${from}, ${to})`, fg: "#ffffff" };
      }
      return { background: gradientFrom(parts[0]), fg: "#ffffff" };
    }
    const hit = BY_ID.get(skin);
    if (hit) return { background: hit.background, fg: hit.fg };
  }
  return { background: gradientFrom(color ?? "#7c3aed"), fg: "#ffffff" };
}

/** Representative solid color for a skin (for the wallet's chart dot). */
export function skinAccent(skin: string | null | undefined): string | null {
  if (!skin) return null;
  if (skin.startsWith("grad:")) return skin.slice(5).split(",")[0];
  if (skin.startsWith("img:")) return null;
  return BY_ID.get(skin)?.accent ?? null;
}

/** Whether the skin carries its own image (so the card adds a legibility scrim). */
export function isImageSkin(skin: string | null | undefined): boolean {
  return !!skin && skin.startsWith("img:");
}

/** Decorative motif for a skin: card skins → chip, cash skins → money art. */
export function skinArt(skin: string | null | undefined): SkinArt {
  if (!skin) return "chip";
  if (skin.startsWith("img:")) return "none";
  if (skin.startsWith("grad:")) return "chip";
  return BY_ID.get(skin)?.art ?? "chip";
}
