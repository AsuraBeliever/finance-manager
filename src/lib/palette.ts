// Chart palette. Category/slice colors are mid-tone and read on both themes,
// so they (and the wallet/category swatches) stay constant. Only the chart
// chrome — axes, grid, tooltip surface — flips with the theme, via
// useChartTokens().
import { useResolvedTheme } from "./theme";

// Category colors: jade and champagne-gold lead, then muted supporting hues.
// Shared by every chart and the wallet/category color swatches.
export const CHART_COLORS = [
  "#25c290", // jade / emerald
  "#d9b45c", // champagne gold
  "#7c5cff", // violet (light-theme primary)
  "#d98a5b", // terracotta
  "#7fa6c4", // dusty blue
  "#b58bb5", // mauve
  "#9bb37e", // sage
  "#cf7f6e", // clay
];

// Neutral fallback for an uncolored wallet/category dot.
export const NEUTRAL_DOT = "#a8a29e";

// Flow semantics (fine on both themes).
export const POSITIVE = "#16a47a";
export const NEGATIVE = "#e5484d";

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
  axis: "#8c857a",
  grid: "#342e24",
  tooltip: {
    backgroundColor: "#262019",
    border: "1px solid #342e24",
    borderRadius: 10,
    color: "#f5f1ea",
  },
};

const LIGHT_CHROME = {
  axis: "#9b98ab",
  grid: "#e6e4ef",
  tooltip: {
    backgroundColor: "#ffffff",
    border: "1px solid #e6e4ef",
    borderRadius: 10,
    color: "#211d35",
  },
};

/** Theme-aware chart tokens for recharts (axes, grid, tooltip surface). */
export function useChartTokens(): ChartTokens {
  const chrome = useResolvedTheme() === "light" ? LIGHT_CHROME : DARK_CHROME;
  return { colors: CHART_COLORS, positive: POSITIVE, negative: NEGATIVE, ...chrome };
}
