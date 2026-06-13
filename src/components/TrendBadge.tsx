import { TrendingDown, TrendingUp } from "lucide-react";

interface TrendBadgeProps {
  /** Change in basis points (e.g. 210 = +2.1%). */
  bps: number;
  /** When false (e.g. expenses), an increase is bad → red. Default true. */
  goodWhenUp?: boolean;
}

/** A small ▲/▼ percentage chip, colored by whether the change is favorable. */
export function TrendBadge({ bps, goodWhenUp = true }: TrendBadgeProps) {
  if (bps === 0) return null;
  const up = bps > 0;
  const favorable = up === goodWhenUp;
  const Icon = up ? TrendingUp : TrendingDown;
  const pct = (Math.abs(bps) / 100).toFixed(1);
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium tabular-nums ${
        favorable ? "bg-accent/15 text-accent" : "bg-danger/15 text-danger"
      }`}
    >
      <Icon size={13} />
      {up ? "+" : "−"}
      {pct}%
    </span>
  );
}
