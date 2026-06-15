import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
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

/** Scales its single-line content down so it never exceeds the parent width. */
function FitText({ children, className }: { children: ReactNode; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [scale, setScale] = useState(1);
  useLayoutEffect(() => {
    const el = ref.current;
    const parent = el?.parentElement;
    if (!el || !parent) return;
    const fit = () => {
      const avail = parent.clientWidth;
      const w = el.scrollWidth;
      setScale(w > avail && w > 0 ? avail / w : 1);
    };
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(parent);
    return () => ro.disconnect();
  }, [children]);
  return (
    <span
      ref={ref}
      className={className}
      style={{
        display: "inline-block",
        whiteSpace: "nowrap",
        transform: `scale(${scale})`,
        transformOrigin: "center",
      }}
    >
      {children}
    </span>
  );
}

/** Circular progress ring with the value enclosed in the center hollow. The
 *  value auto-scales to fit the hollow, so long amounts never overflow. */
export function Gauge({ value, color, label, sublabel, size = 156 }: GaugeProps) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  const data = [{ value: pct, fill: color ?? "var(--color-accent)" }];
  // Inner hollow diameter is ~74% of the ring; keep the text a bit inside it.
  const hollow = Math.round(size * 0.66);
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
      <div
        className="absolute inset-0 m-auto flex flex-col items-center justify-center text-center"
        style={{ width: hollow }}
      >
        <FitText className="font-display text-2xl font-semibold tabular-nums text-fg">
          {label}
        </FitText>
        {sublabel && (
          <span className="mt-0.5 line-clamp-2 text-[0.7rem] leading-tight text-fg-subtle">
            {sublabel}
          </span>
        )}
      </div>
    </div>
  );
}
