import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Gauge } from "../../../components/Gauge";
import { ProgressBar } from "../../../components/ProgressBar";
import { StatWidget } from "../../../components/StatWidget";
import { listSavingsGoals } from "../../../lib/api";
import { formatCents } from "../../../lib/money";
import { CHART_COLORS } from "../../../lib/palette";
import type { Period } from "../../../lib/types";
import { es } from "../../../i18n/es";

export function GoalsWidget({ period }: { period: Period }) {
  const q = useQuery({ queryKey: ["savingsGoals", period], queryFn: () => listSavingsGoals(period) });
  const goals = q.data;
  if (!goals) return null;
  // Keep the cell at a fixed size when no goal existed yet in this period.
  if (goals.length === 0) {
    return (
      <StatWidget title={es.goals.title}>
        <div className="flex h-full items-center justify-center">
          <p className="text-sm text-fg-subtle">{es.dashboard.noPeriodData}</p>
        </div>
      </StatWidget>
    );
  }

  const [featured, ...rest] = goals;

  return (
    <StatWidget
      title={es.goals.title}
      action={
        <Link to="/metas" className="text-xs font-medium text-accent hover:underline">
          {es.dashboard.viewAll}
        </Link>
      }
    >
      <Gauge
        value={featured.progressBps / 10000}
        color={featured.color ?? "var(--color-accent)"}
        label={formatCents(featured.savedCents, featured.currencyCode)}
        sublabel={`${es.goals.of} ${formatCents(featured.targetCents, featured.currencyCode)} · ${featured.name}`}
      />
      {rest.length > 0 && (
        <ul className="mt-4 space-y-3">
          {rest.slice(0, 3).map((g, i) => (
            <li key={g.id}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="truncate text-fg-muted">{g.name}</span>
                <span className="tabular-nums text-fg-subtle">
                  {formatCents(g.savedCents, g.currencyCode)} {es.goals.of}{" "}
                  {formatCents(g.targetCents, g.currencyCode)}
                </span>
              </div>
              <ProgressBar
                value={g.progressBps / 10000}
                color={g.color ?? CHART_COLORS[(i + 1) % CHART_COLORS.length]}
              />
            </li>
          ))}
        </ul>
      )}
    </StatWidget>
  );
}
