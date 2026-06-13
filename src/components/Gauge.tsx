import type { ReactNode } from "react";
import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from "recharts";

interface GaugeProps {
  /** 0..1 fraction. */
  value: number;
  color?: string;
  /** Centered label (e.g. the saved amount). */
  label: ReactNode;
  /** Secondary line under the label (e.g. "de $1,200"). */
  sublabel?: ReactNode;
  /** Diameter in px. */
  size?: number;
}

/** Circular progress ring with the value enclosed in the center hollow — never
 *  overlaps the band, whatever the amount length. */
export function Gauge({ value, color, label, sublabel, size = 156 }: GaugeProps) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  const data = [{ value: pct, fill: color ?? "var(--color-accent)" }];
  return (
    <div className="relative mx-auto" style={{ width: size, height: size }}>
      <ResponsiveContainer width="100%" height="100%">
        <RadialBarChart
          innerRadius="74%"
          outerRadius="100%"
          startAngle={90}
          endAngle={-270}
          data={data}
        >
          <PolarAngleAxis type="number" domain={[0, 100]} tick={false} axisLine={false} />
          <RadialBar
            dataKey="value"
            cornerRadius={20}
            background={{ fill: "var(--color-surface-overlay)" }}
          />
        </RadialBarChart>
      </ResponsiveContainer>
      <div className="absolute inset-0 flex flex-col items-center justify-center px-5 text-center">
        <span className="font-display text-xl font-semibold tabular-nums text-fg">
          {label}
        </span>
        {sublabel && (
          <span className="mt-0.5 line-clamp-2 text-[0.7rem] leading-tight text-fg-subtle">
            {sublabel}
          </span>
        )}
      </div>
    </div>
  );
}
