// Chart palette. Category/slice colors are mid-tone and read on both themes,
// so they (and the wallet/category swatches) stay constant. Only the chart
// chrome — axes, grid, tooltip surface — flips with the theme, via
// useChartTokens().
import { useResolvedTheme } from "./theme";

// Vibrant anime-pop category colors: violet · pink · cyan lead, then bright
// supporting hues. Shared by every chart and the wallet/category swatches.
export const CHART_COLORS = [
  "#a855f7", // violet
  "#ec4899", // pink
  "#22d3ee", // cyan
  "#f59e0b", // amber
  "#34d399", // emerald
  "#60a5fa", // blue
  "#fb7185", // rose
  "#c084fc", // light violet
];

// Neutral fallback for an uncolored wallet/category dot.
export const NEUTRAL_DOT = "#9a93b5";

// Flow semantics (fine on both themes).
export const POSITIVE = "#34d399";
export const NEGATIVE = "#fb7185";

export interface ChartTokens {
  colors: string[];
  positive: string;
  negative: string;
  axis: string;
  grid: string;
  tooltip: {
    backgroundColor: string;
    border: string;
    borderRadius: number;
    color: string;
  };
}

const DARK_CHROME = {
  axis: "#6f6a8d",
  grid: "rgba(255,255,255,0.08)",
  tooltip: {
    backgroundColor: "#1b1830",
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 12,
    color: "#f2efff",
  },
};

const LIGHT_CHROME = {
  axis: "#9a93b5",
  grid: "rgba(124,58,237,0.12)",
  tooltip: {
    backgroundColor: "#ffffff",
    border: "1px solid rgba(124,58,237,0.16)",
    borderRadius: 12,
    color: "#211a3a",
  },
};

/** Theme-aware chart tokens for recharts (axes, grid, tooltip surface). */
export function useChartTokens(): ChartTokens {
  const chrome = useResolvedTheme() === "light" ? LIGHT_CHROME : DARK_CHROME;
  return { colors: CHART_COLORS, positive: POSITIVE, negative: NEGATIVE, ...chrome };
}
