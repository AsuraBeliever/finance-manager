// Editorial chart palette: jade and champagne-gold lead, followed by muted
// supporting hues tuned to sit on the warm charcoal canvas. Shared by every
// chart and the wallet/category color swatches so dots and slices agree.
export const CHART_COLORS = [
  "#25c290", // jade
  "#d9b45c", // champagne gold
  "#d98a5b", // terracotta
  "#7fa6c4", // dusty blue
  "#b58bb5", // mauve
  "#9bb37e", // sage
  "#cf7f6e", // clay
];

// Neutral fallback for an uncolored wallet/category dot.
export const NEUTRAL_DOT = "#a8a29e";

// Flow semantics.
export const POSITIVE = "#25c290";
export const NEGATIVE = "#f0786b";

// Recharts surface chrome, matched to the theme tokens.
export const AXIS_STROKE = "#8c857a";
export const GRID_STROKE = "#342e24";
export const TOOLTIP_STYLE = {
  backgroundColor: "#262019",
  border: "1px solid #342e24",
  borderRadius: 10,
  color: "#f5f5f4",
} as const;
