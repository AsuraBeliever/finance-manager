import type { ReactNode } from "react";
import {
  PolarAngleAxis,
  RadialBar,
  RadialBarChart,
  ResponsiveContainer,
} from "recharts";

interface GaugeProps {
  /** 0..1 fraction. */
  value: number;
  color?: string;
  /** Big centered label (e.g. the saved amount). */
  label: ReactNode;
  /** Secondary line under the label (e.g. "de $1,200"). */
  sublabel?: ReactNode;
  height?: number;
}

/** Semicircular progress gauge (the reference "Saving Goal" widget). */
export function Gauge({ value, color, label, sublabel, height = 170 }: GaugeProps) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  const data = [{ value: pct, fill: color ?? "var(--color-accent)" }];
  return (
    <div className="relative" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <RadialBarChart
          innerRadius="78%"
          outerRadius="100%"
          startAngle={180}
          endAngle={0}
          data={data}
          cy="100%"
        >
          <PolarAngleAxis type="number" domain={[0, 100]} tick={false} axisLine={false} />
          <RadialBar
            dataKey="value"
            cornerRadius={20}
            background={{ fill: "var(--color-surface-overlay)" }}
          />
        </RadialBarChart>
      </ResponsiveContainer>
      <div className="absolute inset-x-0 bottom-1 flex flex-col items-center">
        <span className="font-display text-3xl font-semibold tabular-nums text-fg">
          {label}
        </span>
        {sublabel && <span className="mt-0.5 text-xs text-fg-subtle">{sublabel}</span>}
      </div>
    </div>
  );
}
