import { useQuery } from "@tanstack/react-query";
import { Eye, EyeOff, LayoutDashboard, RotateCcw } from "lucide-react";
import { useState } from "react";
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
import { Button } from "../../components/Button";
import { DashboardGrid, type GridItemSpec } from "../../components/DashboardGrid";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { StatWidget } from "../../components/StatWidget";
import { TrendBadge } from "../../components/TrendBadge";
import {
  getCategoryBreakdown,
  getDashboardSummary,
  getSpendingTrends,
  listBudgets,
  listSavingsGoals,
  listSubscriptions,
} from "../../lib/api";
import { formatCents } from "../../lib/money";
import { MASK, useHideBalance } from "../../lib/hideBalance";
import { CHART_COLORS, NEGATIVE, POSITIVE, useChartTokens } from "../../lib/palette";
import { es } from "../../i18n/es";
import { BreakdownWidget } from "./widgets/BreakdownWidget";
import { BudgetWidget } from "./widgets/BudgetWidget";
import { GoalsWidget } from "./widgets/GoalsWidget";
import { SubscriptionsWidget } from "./widgets/SubscriptionsWidget";

function monthLabel(month: string): string {
  const [y, m] = month.split("-");
  const names = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  return `${names[Number(m) - 1]} ${y.slice(2)}`;
}

const longDateFmt = new Intl.DateTimeFormat("es-MX", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
});

// The daily flow chart spans the current month, so its axis only carries the
// day-of-month ('DD'); expand it to a full localized date for the tooltip.
function dayLabel(day: string): string {
  const n = Number(day);
  if (!Number.isFinite(n)) return day;
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), n);
  const full = longDateFmt.format(date);
  return full.charAt(0).toUpperCase() + full.slice(1);
}

export function DashboardPage() {
  const chart = useChartTokens();
  const [hidden, toggleHidden] = useHideBalance();
  const summary = useQuery({ queryKey: ["dashboard"], queryFn: getDashboardSummary });
  const trends = useQuery({ queryKey: ["spendingTrends"], queryFn: getSpendingTrends });
  // Same keys the widgets use (deduped) — only to decide which cells exist.
  const budgets = useQuery({ queryKey: ["budgets"], queryFn: listBudgets });
  const goals = useQuery({ queryKey: ["savingsGoals"], queryFn: listSavingsGoals });
  const subs = useQuery({ queryKey: ["subscriptions"], queryFn: listSubscriptions });
  const expBreak = useQuery({
    queryKey: ["breakdown", "expense"],
    queryFn: () => getCategoryBreakdown("expense", "month"),
  });
  const incBreak = useQuery({
    queryKey: ["breakdown", "income"],
    queryFn: () => getCategoryBreakdown("income", "month"),
  });
  const [resetSignal, setResetSignal] = useState(0);

  if (summary.isPending)
    return <p className="text-sm text-fg-subtle">{es.common.loading}</p>;
  if (summary.isError)
    return <p className="text-sm text-danger">{String(summary.error)}</p>;

  const s = summary.data;
  const hasData = s.wallets.length > 0 || s.investmentsTotalMxnCents > 0;
  const money = (cents: number, code = "MXN") => (hidden ? MASK : formatCents(cents, code));

  const walletDonut = s.wallets
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

  const monthlyData = s.monthly.map((m) => ({
    month: monthLabel(m.month),
    [es.dashboard.incomes]: m.incomeMxnCents / 100,
    [es.dashboard.expenses]: m.expenseMxnCents / 100,
  }));

  const t = trends.data;
  const dailyData =
    t?.daily.map((d) => ({
      day: d.day,
      [es.dashboard.incomes]: d.incomeMxnCents / 100,
      [es.dashboard.expenses]: d.expenseMxnCents / 100,
    })) ?? [];

  if (!hasData) {
    return (
      <>
        <PageHeader title={es.dashboard.title} />
        <EmptyState
          icon={LayoutDashboard}
          title={es.dashboard.emptyTitle}
          description={es.dashboard.emptyDescription}
        />
      </>
    );
  }

  // Net worth hero, as a fill-height card for the grid.
  const heroNode = (
    <section className="relative flex h-full flex-col overflow-auto rounded-2xl border border-border-muted bg-surface-raised p-6 shadow-card md:p-7">
      <div className="pointer-events-none absolute -top-24 -right-16 h-64 w-64 rounded-full bg-accent/10 blur-3xl" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-gold/40 to-transparent" />

      <div className="flex items-center gap-2">
        <p className="eyebrow">{es.dashboard.netWorth}</p>
        <button
          onClick={toggleHidden}
          title={hidden ? es.dashboard.showBalance : es.dashboard.hideBalance}
          className="rounded-md p-1 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
        >
          {hidden ? <EyeOff size={15} /> : <Eye size={15} />}
        </button>
      </div>
      <p className="text-gold-gradient mt-2 font-display text-4xl font-semibold tracking-tight tabular-nums md:text-5xl">
        {money(s.totalMxnCents + s.investmentsTotalMxnCents)}
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-fg-muted">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-accent" />
          {es.nav.wallets} <span className="tabular-nums text-fg">{money(s.totalMxnCents)}</span>
        </span>
        {s.investmentsTotalMxnCents > 0 && (
          <>
            <span className="text-border-muted">·</span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-gold" />
              {es.investments.total}{" "}
              <span className="tabular-nums text-fg">{money(s.investmentsTotalMxnCents)}</span>
            </span>
          </>
        )}
      </div>

      {t && (t.incomeMxnCents > 0 || t.expenseMxnCents > 0) && (
        <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 border-t border-border-muted pt-4">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted">{es.dashboard.incomes}</span>
            <span className="tabular-nums text-fg">{money(t.incomeMxnCents)}</span>
            <TrendBadge bps={t.incomeTrendBps} />
          </div>
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted">{es.dashboard.expenses}</span>
            <span className="tabular-nums text-fg">{money(t.expenseMxnCents)}</span>
            <TrendBadge bps={t.expenseTrendBps} goodWhenUp={false} />
          </div>
          <span className="self-center text-xs text-fg-subtle">{es.dashboard.vsLastMonth}</span>
        </div>
      )}

      {s.byCurrency.length > 1 && (
        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1">
          {s.byCurrency.map((c) => (
            <span key={c.currencyCode} className="text-sm text-fg-muted">
              <span className="font-mono text-fg">{c.currencyCode}</span>{" "}
              {money(c.balanceCents, c.currencyCode)}
              {!c.hasRate && <span className="text-danger"> · {es.dashboard.noRate}</span>}
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
  );

  const items: GridItemSpec[] = [
    { key: "networth", w: 12, h: 4, minW: 4, minH: 3, node: heroNode },
  ];

  if (dailyData.length > 0) {
    items.push({
      key: "dailyFlow",
      w: 8,
      h: 5,
      minH: 3,
      node: (
        <StatWidget title={es.dashboard.dailyFlow}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={dailyData}>
              <XAxis dataKey="day" stroke={chart.axis} fontSize={11} />
              <YAxis stroke={chart.axis} fontSize={11} width={40} />
              <Tooltip
                labelFormatter={(label) => dayLabel(String(label))}
                formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                contentStyle={chart.tooltip}
                cursor={{ fill: "color-mix(in oklab, var(--color-border-muted) 40%, transparent)" }}
              />
              <Legend />
              <Bar dataKey={es.dashboard.incomes} fill={POSITIVE} radius={[3, 3, 0, 0]} />
              <Bar dataKey={es.dashboard.expenses} fill={NEGATIVE} radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </StatWidget>
      ),
    });
  }
  if ((budgets.data?.length ?? 0) > 0) {
    items.push({ key: "budget", w: 4, h: 5, node: <BudgetWidget /> });
  }
  if ((expBreak.data?.slices.length ?? 0) > 0) {
    items.push({
      key: "breakdownExpense",
      w: 4,
      h: 5,
      node: <BreakdownWidget kind="expense" title={es.dashboard.expenseByCategory} />,
    });
  }
  if ((incBreak.data?.slices.length ?? 0) > 0) {
    items.push({
      key: "breakdownIncome",
      w: 4,
      h: 5,
      node: <BreakdownWidget kind="income" title={es.dashboard.incomeByCategory} />,
    });
  }
  if ((goals.data?.length ?? 0) > 0) {
    items.push({ key: "goals", w: 4, h: 5, node: <GoalsWidget /> });
  }
  if ((subs.data?.subscriptions.length ?? 0) > 0) {
    items.push({ key: "subscriptions", w: 4, h: 5, node: <SubscriptionsWidget /> });
  }
  if (walletDonut.length > 0) {
    items.push({
      key: "byWallet",
      w: 4,
      h: 5,
      node: (
        <StatWidget title={es.dashboard.byWallet}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={walletDonut}
                dataKey="value"
                nameKey="name"
                innerRadius={50}
                outerRadius={80}
                paddingAngle={2}
                stroke="none"
              >
                {walletDonut.map((d) => (
                  <Cell key={d.name} fill={d.color} />
                ))}
              </Pie>
              <Tooltip
                formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                contentStyle={chart.tooltip}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </StatWidget>
      ),
    });
  }
  if (investmentsDonut.length > 0) {
    items.push({
      key: "byInvestment",
      w: 4,
      h: 5,
      node: (
        <StatWidget title={es.dashboard.byInvestment}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={investmentsDonut}
                dataKey="value"
                nameKey="name"
                innerRadius={50}
                outerRadius={80}
                paddingAngle={2}
                stroke="none"
              >
                {investmentsDonut.map((d) => (
                  <Cell key={d.name} fill={d.color} />
                ))}
              </Pie>
              <Tooltip
                formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                contentStyle={chart.tooltip}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </StatWidget>
      ),
    });
  }
  if (monthlyData.length > 0) {
    items.push({
      key: "monthlyFlow",
      w: 12,
      h: 5,
      minH: 3,
      node: (
        <StatWidget title={es.dashboard.monthlyFlow}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={monthlyData}>
              <XAxis dataKey="month" stroke={chart.axis} fontSize={12} />
              <YAxis stroke={chart.axis} fontSize={12} />
              <Tooltip
                formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
                contentStyle={chart.tooltip}
                cursor={{ fill: "color-mix(in oklab, var(--color-border-muted) 40%, transparent)" }}
              />
              <Legend />
              <Bar dataKey={es.dashboard.incomes} fill={POSITIVE} radius={[4, 4, 0, 0]} />
              <Bar dataKey={es.dashboard.expenses} fill={NEGATIVE} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </StatWidget>
      ),
    });
  }

  return (
    <>
      <PageHeader
        title={es.dashboard.title}
        actions={
          <Button variant="ghost" onClick={() => setResetSignal((n) => n + 1)}>
            <span className="flex items-center gap-2">
              <RotateCcw size={15} /> {es.dashboard.resetLayout}
            </span>
          </Button>
        }
      />
      <DashboardGrid items={items} resetSignal={resetSignal} />
    </>
  );
}
