import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Gauge } from "../../../components/Gauge";
import { ProgressBar } from "../../../components/ProgressBar";
import { StatWidget } from "../../../components/StatWidget";
import { listSavingsGoals } from "../../../lib/api";
import { formatCents } from "../../../lib/money";
import { CHART_COLORS } from "../../../lib/palette";
import { es } from "../../../i18n/es";

export function GoalsWidget() {
  const q = useQuery({ queryKey: ["savingsGoals"], queryFn: listSavingsGoals });
  const goals = q.data;
  if (!goals || goals.length === 0) return null;

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
