import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { BrandLogo } from "../../../components/BrandLogo";
import { StatWidget } from "../../../components/StatWidget";
import { listSubscriptions } from "../../../lib/api";
import { formatCents } from "../../../lib/money";
import { CHART_COLORS } from "../../../lib/palette";
import type { Period } from "../../../lib/types";
import { es } from "../../../i18n/es";

export function SubscriptionsWidget({ period }: { period: Period }) {
  const q = useQuery({ queryKey: ["subscriptions", period], queryFn: () => listSubscriptions(period) });
  const data = q.data;
  if (!data) return null;
  // Only the subscriptions actually charged in the selected period.
  const charged = data.subscriptions.filter((s) => s.chargedInPeriod);

  if (charged.length === 0) {
    return (
      <StatWidget title={es.subscriptions.title}>
        <div className="flex h-full items-center justify-center">
          <p className="text-sm text-fg-subtle">{es.dashboard.noPeriodData}</p>
        </div>
      </StatWidget>
    );
  }

  return (
    <StatWidget
      title={es.subscriptions.title}
      action={
        <Link to="/suscripciones" className="text-xs font-medium text-accent hover:underline">
          {es.dashboard.viewAll}
        </Link>
      }
    >
      <p className="mb-3 text-sm text-fg-muted">
        {es.subscriptions.chargedInPeriod}:{" "}
        <span className="font-display text-base font-semibold tabular-nums text-fg">
          {formatCents(data.monthlyTotalMxnCents, "MXN")}
        </span>
      </p>
      <ul className="divide-y divide-border-muted">
        {charged.slice(0, 5).map((s, i) => {
          const color = s.color ?? CHART_COLORS[i % CHART_COLORS.length];
          return (
            <li key={s.id} className="flex items-center gap-3 py-2.5">
              <span
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-sm font-semibold text-white"
                style={{ backgroundColor: color }}
              >
                <BrandLogo slug={s.icon} size={20} fallback={s.name.charAt(0).toUpperCase()} />
              </span>
              <div className="min-w-0 flex-1">
                <p className={`truncate text-sm font-medium ${s.isActive ? "text-fg" : "text-fg-subtle"}`}>
                  {s.name}
                </p>
                <p className="text-xs text-fg-subtle">{s.nextChargeDate}</p>
              </div>
              <span className="shrink-0 tabular-nums text-sm font-medium text-fg">
                {formatCents(s.amountCents, s.currencyCode)}
              </span>
            </li>
          );
        })}
      </ul>
    </StatWidget>
  );
}
