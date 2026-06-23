import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ProgressBar } from "../../../components/ProgressBar";
import { StatWidget } from "../../../components/StatWidget";
import { listBudgets } from "../../../lib/api";
import { formatCents } from "../../../lib/money";
import type { Period } from "../../../lib/types";
import { es } from "../../../i18n/es";
import { seedName } from "../../../i18n/seed";

export function BudgetWidget({ period }: { period: Period }) {
  const q = useQuery({ queryKey: ["budgets", period], queryFn: () => listBudgets(period) });
  const budgets = q.data;
  if (!budgets || budgets.length === 0) return null;

  const overall = budgets.find((b) => b.categoryId === null);
  const perCategory = budgets.filter((b) => b.categoryId !== null);

  return (
    <StatWidget
      title={es.budgets.spendingLimit}
      action={
        <Link to="/presupuestos" className="text-xs font-medium text-accent hover:underline">
          {es.dashboard.viewAll}
        </Link>
      }
    >
      {overall && (
        <div className="mb-4">
          <p className="font-display text-3xl font-semibold tabular-nums text-fg">
            {formatCents(overall.spentMxnCents, "MXN")}
            <span className="ml-2 text-sm font-normal text-fg-subtle">
              {es.goals.of} {formatCents(overall.limitCents, "MXN")}
            </span>
          </p>
          <ProgressBar
            className="mt-3"
            value={overall.progressBps / 10000}
            color="var(--color-gold)"
            segments={10}
          />
        </div>
      )}

      {perCategory.length > 0 && (
        <ul className="space-y-3">
          {perCategory.map((b) => (
            <li key={b.id}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-fg-muted">
                  {b.color && (
                    <span
                      className="h-2.5 w-2.5 rounded-full"
                      style={{ backgroundColor: b.color }}
                    />
                  )}
                  {seedName(b.categoryName)}
                </span>
                <span className="tabular-nums text-fg-subtle">
                  {formatCents(b.spentMxnCents, "MXN")} / {formatCents(b.limitCents, "MXN")}
                </span>
              </div>
              <ProgressBar value={b.progressBps / 10000} color={b.color ?? undefined} />
            </li>
          ))}
        </ul>
      )}
    </StatWidget>
  );
}
