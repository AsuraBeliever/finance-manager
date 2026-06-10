import { useQuery } from "@tanstack/react-query";
import { LayoutDashboard } from "lucide-react";
import {
  Bar,
  BarChart,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { getDashboardSummary } from "../../lib/api";
import { formatCents } from "../../lib/money";
import { es } from "../../i18n/es";

const FALLBACK_COLORS = ["#34d399", "#60a5fa", "#f472b6", "#fbbf24", "#a78bfa", "#f87171", "#94a3b8"];

function monthLabel(month: string): string {
  const [y, m] = month.split("-");
  const names = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  return `${names[Number(m) - 1]} ${y.slice(2)}`;
}

export function DashboardPage() {
  const summary = useQuery({
    queryKey: ["dashboard"],
    queryFn: getDashboardSummary,
  });

  if (summary.isPending)
    return <p className="text-sm text-zinc-500">{es.common.loading}</p>;
  if (summary.isError)
    return <p className="text-sm text-danger">{String(summary.error)}</p>;

  const s = summary.data;
  const hasData = s.wallets.length > 0 || s.investmentsTotalMxnCents > 0;

  const donutData = s.wallets
    .filter((w) => w.balanceMxnCents > 0)
    .map((w, i) => ({
      name: w.name,
      value: w.balanceMxnCents / 100,
      color: w.color ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length],
    }));

  const barData = s.monthly.map((m) => ({
    month: monthLabel(m.month),
    [es.dashboard.incomes]: m.incomeMxnCents / 100,
    [es.dashboard.expenses]: m.expenseMxnCents / 100,
  }));

  return (
    <>
      <PageHeader title={es.dashboard.title} />

      {!hasData ? (
        <EmptyState
          icon={LayoutDashboard}
          title={es.dashboard.emptyTitle}
          description={es.dashboard.emptyDescription}
        />
      ) : (
        <div className="grid gap-4">
          <section className="rounded-xl border border-border-muted bg-surface-raised p-6">
            <p className="text-sm text-zinc-400">{es.dashboard.netWorth}</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums text-accent">
              {formatCents(s.totalMxnCents + s.investmentsTotalMxnCents, "MXN")}
            </p>
            {s.investmentsTotalMxnCents > 0 && (
              <p className="mt-1 text-sm text-zinc-400">
                {es.nav.wallets}: {formatCents(s.totalMxnCents, "MXN")} ·{" "}
                {es.investments.total}:{" "}
                {formatCents(s.investmentsTotalMxnCents, "MXN")}
              </p>
            )}
            {s.byCurrency.length > 1 && (
              <div className="mt-4 flex flex-wrap gap-x-6 gap-y-1">
                {s.byCurrency.map((c) => (
                  <span key={c.currencyCode} className="text-sm text-zinc-400">
                    <span className="font-mono text-zinc-300">{c.currencyCode}</span>{" "}
                    {formatCents(c.balanceCents, c.currencyCode)}
                    {!c.hasRate && (
                      <span className="text-danger"> · {es.dashboard.noRate}</span>
                    )}
                  </span>
                ))}
              </div>
            )}
            {s.missingRates.length > 0 && (
              <p className="mt-2 text-xs text-danger">
                {es.dashboard.missingRates} {s.missingRates.join(", ")}
              </p>
            )}
          </section>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            {donutData.length > 0 && (
              <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
                <h3 className="mb-2 font-medium">{es.dashboard.byWallet}</h3>
                <ResponsiveContainer width="100%" height={260}>
                  <PieChart>
                    <Pie
                      data={donutData}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={60}
                      outerRadius={95}
                      paddingAngle={2}
                      stroke="none"
                    >
                      {donutData.map((d) => (
                        <Cell key={d.name} fill={d.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                      contentStyle={{
                        backgroundColor: "#1f2330",
                        border: "1px solid #2a2f3d",
                        borderRadius: 8,
                      }}
                    />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </section>
            )}

            {barData.length > 0 && (
              <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
                <h3 className="mb-2 font-medium">{es.dashboard.monthlyFlow}</h3>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={barData}>
                    <XAxis dataKey="month" stroke="#71717a" fontSize={12} />
                    <YAxis stroke="#71717a" fontSize={12} />
                    <Tooltip
                      formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                      contentStyle={{
                        backgroundColor: "#1f2330",
                        border: "1px solid #2a2f3d",
                        borderRadius: 8,
                      }}
                      cursor={{ fill: "#2a2f3d55" }}
                    />
                    <Legend />
                    <Bar dataKey={es.dashboard.incomes} fill="#34d399" radius={[3, 3, 0, 0]} />
                    <Bar dataKey={es.dashboard.expenses} fill="#f87171" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </section>
            )}
          </div>
        </div>
      )}
    </>
  );
}
