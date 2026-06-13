import type { ReactNode } from "react";
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
import {
  AXIS_STROKE,
  CHART_COLORS,
  NEGATIVE,
  POSITIVE,
  TOOLTIP_STYLE,
} from "../../lib/palette";
import { es } from "../../i18n/es";

function monthLabel(month: string): string {
  const [y, m] = month.split("-");
  const names = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  return `${names[Number(m) - 1]} ${y.slice(2)}`;
}

/** Editorial chart panel: a serif heading over a framed plot. */
function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card">
      <h3 className="mb-4 font-display text-lg font-medium tracking-tight text-stone-100">
        {title}
      </h3>
      {children}
    </section>
  );
}

export function DashboardPage() {
  const summary = useQuery({
    queryKey: ["dashboard"],
    queryFn: getDashboardSummary,
  });

  if (summary.isPending)
    return <p className="text-sm text-stone-500">{es.common.loading}</p>;
  if (summary.isError)
    return <p className="text-sm text-danger">{String(summary.error)}</p>;

  const s = summary.data;
  const hasData = s.wallets.length > 0 || s.investmentsTotalMxnCents > 0;

  const donutData = s.wallets
    .filter((w) => w.balanceMxnCents > 0)
    .map((w, i) => ({
      name: w.name,
      value: w.balanceMxnCents / 100,
      color: w.color ?? CHART_COLORS[i % CHART_COLORS.length],
    }));

  const investmentsDonut = s.investments
    .filter((inv) => inv.valueMxnCents > 0)
    .map((inv, i) => ({
      name: inv.name,
      value: inv.valueMxnCents / 100,
      color: CHART_COLORS[i % CHART_COLORS.length],
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
          <section className="relative overflow-hidden rounded-2xl border border-border-muted bg-surface-raised p-7 shadow-card md:p-9">
            {/* Atmosphere kept inside the hero: a jade bloom and a gold hairline. */}
            <div className="pointer-events-none absolute -top-24 -right-16 h-64 w-64 rounded-full bg-accent/10 blur-3xl" />
            <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-gold/40 to-transparent" />

            <p className="eyebrow">{es.dashboard.netWorth}</p>
            <p className="text-gold-gradient mt-3 font-display text-5xl font-semibold tracking-tight tabular-nums md:text-6xl">
              {formatCents(s.totalMxnCents + s.investmentsTotalMxnCents, "MXN")}
            </p>
            {s.investmentsTotalMxnCents > 0 && (
              <p className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-stone-400">
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-accent" />
                  {es.nav.wallets}{" "}
                  <span className="tabular-nums text-stone-300">
                    {formatCents(s.totalMxnCents, "MXN")}
                  </span>
                </span>
                <span className="text-border-muted">·</span>
                <span className="inline-flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-gold" />
                  {es.investments.total}{" "}
                  <span className="tabular-nums text-stone-300">
                    {formatCents(s.investmentsTotalMxnCents, "MXN")}
                  </span>
                </span>
              </p>
            )}
            {s.byCurrency.length > 1 && (
              <div className="mt-5 flex flex-wrap gap-x-6 gap-y-1 border-t border-border-muted pt-4">
                {s.byCurrency.map((c) => (
                  <span key={c.currencyCode} className="text-sm text-stone-400">
                    <span className="font-mono text-stone-300">{c.currencyCode}</span>{" "}
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
              <ChartCard title={es.dashboard.byWallet}>
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
                      contentStyle={TOOLTIP_STYLE}
                    />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </ChartCard>
            )}

            {investmentsDonut.length > 0 && (
              <ChartCard title={es.dashboard.byInvestment}>
                <ResponsiveContainer width="100%" height={260}>
                  <PieChart>
                    <Pie
                      data={investmentsDonut}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={60}
                      outerRadius={95}
                      paddingAngle={2}
                      stroke="none"
                    >
                      {investmentsDonut.map((d) => (
                        <Cell key={d.name} fill={d.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                      contentStyle={TOOLTIP_STYLE}
                    />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </ChartCard>
            )}

            {barData.length > 0 && (
              <ChartCard title={es.dashboard.monthlyFlow}>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={barData}>
                    <XAxis dataKey="month" stroke={AXIS_STROKE} fontSize={12} />
                    <YAxis stroke={AXIS_STROKE} fontSize={12} />
                    <Tooltip
                      formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                      contentStyle={TOOLTIP_STYLE}
                      cursor={{ fill: "#342e2455" }}
                    />
                    <Legend />
                    <Bar dataKey={es.dashboard.incomes} fill={POSITIVE} radius={[4, 4, 0, 0]} />
                    <Bar dataKey={es.dashboard.expenses} fill={NEGATIVE} radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </ChartCard>
            )}
          </div>
        </div>
      )}
    </>
  );
}
