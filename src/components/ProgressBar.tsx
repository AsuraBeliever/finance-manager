interface ProgressBarProps {
  /** 0..1 fraction (already computed; e.g. progressBps / 10000). */
  value: number;
  color?: string;
  /** Render as N discrete segments (the reference "spending limit" look). */
  segments?: number;
  className?: string;
}

/** Rounded progress bar. Overflows past 100% turn the danger color. */
export function ProgressBar({ value, color, segments, className = "" }: ProgressBarProps) {
  const pct = Math.max(0, Math.min(1, value));
  const over = value > 1.0001;
  const fill = over ? "var(--color-danger)" : (color ?? "var(--color-accent)");

  if (segments && segments > 0) {
    const filled = Math.round(pct * segments);
    return (
      <div className={`flex gap-1 ${className}`}>
        {Array.from({ length: segments }).map((_, i) => (
          <span
            key={i}
            className="h-2 flex-1 rounded-full"
            style={{ backgroundColor: i < filled ? fill : "var(--color-surface-overlay)" }}
          />
        ))}
      </div>
    );
  }

  return (
    <div
      className={`h-2.5 w-full overflow-hidden rounded-full bg-surface-overlay ${className}`}
    >
      <div
        className="h-full rounded-full transition-[width] duration-500"
        style={{ width: `${pct * 100}%`, backgroundColor: fill }}
      />
    </div>
  );
}
