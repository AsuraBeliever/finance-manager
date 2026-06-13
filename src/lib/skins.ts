// Wallet card "skins". Original gradient art (no bank logos/trademarks); the
// crystalline glass sheen is added by <WalletCard>. A skin value stored on a
// wallet is one of:
//   - a catalog id ("oro", "azul", …)
//   - "grad:<from>,<to>,<angle>"            (custom gradient)
//   - "img:<data-url-or-url>"               (imported image, the user's own)
// NULL falls back to a sensible default derived from the wallet color.

export type SkinGroup = "banco" | "nivel" | "efectivo" | "glass";

export interface Skin {
  id: string;
  label: string;
  group: SkinGroup;
  background: string;
  /** Text/foreground color that reads on this background. */
  fg: string;
}

export const SKINS: Skin[] = [
  // ── color tones (bank-inspired by hue; name your wallet e.g. "BBVA Oro") ──
  { id: "azul", label: "Azul", group: "banco", fg: "#eaf2ff",
    background: "linear-gradient(135deg,#0a3d91 0%,#1565d8 55%,#0a3d91 100%)" },
  { id: "marino", label: "Azul marino", group: "banco", fg: "#e8eefc",
    background: "linear-gradient(135deg,#0b1f4d 0%,#16357e 100%)" },
  { id: "turquesa", label: "Turquesa", group: "banco", fg: "#e9fbff",
    background: "linear-gradient(135deg,#0e7490 0%,#22b8cf 100%)" },
  { id: "rojo", label: "Rojo", group: "banco", fg: "#fff0f0",
    background: "linear-gradient(135deg,#9e1414 0%,#e1232b 60%,#9e1414 100%)" },
  { id: "vino", label: "Vino", group: "banco", fg: "#ffeef2",
    background: "linear-gradient(135deg,#5b0b22 0%,#9c1f3d 100%)" },
  { id: "morado", label: "Morado", group: "banco", fg: "#f6effe",
    background: "linear-gradient(135deg,#5b1ea6 0%,#9333ea 60%,#6d28d9 100%)" },
  { id: "verde", label: "Verde", group: "banco", fg: "#eafff4",
    background: "linear-gradient(135deg,#065f46 0%,#10b981 100%)" },

  // ── tiers ──
  { id: "oro", label: "Oro", group: "nivel", fg: "#3a2c06",
    background: "linear-gradient(135deg,#b8860b 0%,#f5d479 45%,#caa12f 100%)" },
  { id: "platino", label: "Platino", group: "nivel", fg: "#23262e",
    background: "linear-gradient(135deg,#9aa3ad 0%,#e6ebf0 50%,#aeb6c0 100%)" },
  { id: "black", label: "Black", group: "nivel", fg: "#ece9f5",
    background: "linear-gradient(135deg,#0a0a0f 0%,#23232e 55%,#0a0a0f 100%)" },
  { id: "infinite", label: "Infinite", group: "nivel", fg: "#e7eeff",
    background: "linear-gradient(135deg,#0b1026 0%,#1e2a78 50%,#0b1026 100%)" },

  // ── efectivo (cash / wallet) ──
  { id: "efectivo", label: "Efectivo", group: "efectivo", fg: "#eafff0",
    background: "linear-gradient(135deg,#1b5e3a 0%,#2f9e63 50%,#1b5e3a 100%)" },
  { id: "cuero", label: "Cuero", group: "efectivo", fg: "#fbeede",
    background: "linear-gradient(135deg,#5a3413 0%,#8a5a2b 55%,#4a2a0f 100%)" },
  { id: "billete", label: "Billete", group: "efectivo", fg: "#eafdf2",
    background: "linear-gradient(135deg,#14532d 0%,#22663c 50%,#0f3d22 100%)" },

  // ── neon glass (matches the app) ──
  { id: "neon", label: "Neón", group: "glass", fg: "#f4f0ff",
    background: "linear-gradient(135deg,#7c3aed 0%,#a855f7 45%,#22d3ee 110%)" },
  { id: "holo", label: "Holográfico", group: "glass", fg: "#1a1430",
    background: "linear-gradient(135deg,#a5f3fc 0%,#c4b5fd 35%,#fbcfe8 70%,#fde68a 100%)" },
  { id: "noche", label: "Noche", group: "glass", fg: "#e8e6f4",
    background: "linear-gradient(135deg,#141228 0%,#2a2350 100%)" },
];

const BY_ID = new Map(SKINS.map((s) => [s.id, s]));

export interface ResolvedSkin {
  background: string;
  fg: string;
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
      const [from, to, angle = "135"] = skin.slice(5).split(",");
      return { background: `linear-gradient(${angle}deg, ${from}, ${to})`, fg: "#ffffff" };
    }
    const hit = BY_ID.get(skin);
    if (hit) return { background: hit.background, fg: hit.fg };
  }
  // default: a gradient derived from the wallet's accent color (or app violet)
  const c = color ?? "#7c3aed";
  return {
    background: `linear-gradient(135deg, ${c} 0%, color-mix(in oklab, ${c} 55%, #000) 100%)`,
    fg: "#ffffff",
  };
}

/** Whether the imported skin carries its own image (skip gradient sheen tint). */
export function isImageSkin(skin: string | null | undefined): boolean {
  return !!skin && skin.startsWith("img:");
}
