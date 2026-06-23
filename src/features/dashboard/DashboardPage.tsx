import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Eye, EyeOff, LayoutDashboard, RotateCcw } from "lucide-react";
import { useState } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { Button } from "../../components/Button";
import { DashboardGrid, type GridItemSpec } from "../../components/DashboardGrid";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { StatWidget } from "../../components/StatWidget";
import { TrendBadge } from "../../components/TrendBadge";
import {
  getDashboardSummary,
  getSpendingTrends,
  listBudgets,
  listSavingsGoals,
  listSubscriptions,
} from "../../lib/api";
import { formatCents } from "../../lib/money";
import { MASK, useHideBalance } from "../../lib/hideBalance";
import { CHART_COLORS, useChartTokens } from "../../lib/palette";
import { es } from "../../i18n/es";
import { FlowChart } from "./FlowChart";
import { PeriodPicker } from "./PeriodPicker";
import { usePeriod } from "./usePeriod";
import { BreakdownWidget } from "./widgets/BreakdownWidget";
import { BudgetWidget } from "./widgets/BudgetWidget";
import { FlowRangeWidget } from "./widgets/FlowRangeWidget";
import { GoalsWidget } from "./widgets/GoalsWidget";
import { SubscriptionsWidget } from "./widgets/SubscriptionsWidget";

export function DashboardPage() {
  const chart = useChartTokens();
  const [hidden, toggleHidden] = useHideBalance();
  const [period, setPeriod] = usePeriod();
  const summary = useQuery({
    queryKey: ["dashboard", period],
    queryFn: () => getDashboardSummary(period),
  });
  const trends = useQuery({
    queryKey: ["spendingTrends", period],
    queryFn: () => getSpendingTrends(period),
  });
  // Same keys the widgets use (deduped) — only to decide which cells exist.
  const budgets = useQuery({ queryKey: ["budgets"], queryFn: () => listBudgets() });
  const goals = useQuery({ queryKey: ["savingsGoals"], queryFn: () => listSavingsGoals() });
  const subs = useQuery({ queryKey: ["subscriptions"], queryFn: () => listSubscriptions() });
  const [resetSignal, setResetSignal] = useState(0);

  if (summary.isPending)
    return <p className="text-sm text-fg-subtle">{es.common.loading}</p>;
  if (summary.isError)
    return <p className="text-sm text-danger">{String(summary.error)}</p>;

  const s = summary.data;
  const hasData = s.wallets.length > 0 || s.investmentsTotalMxnCents > 0;
  const money = (cents: number, code = "MXN") => (hidden ? MASK : formatCents(cents, code));
  // Net worth (cash + investments) at the start and end of the selected period.
  const netStart = s.totalStartMxnCents + s.investmentsStartMxnCents;
  const netEnd = s.totalEndMxnCents + s.investmentsTotalMxnCents;

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

  const t = trends.data;

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
      <div className="mt-2 flex flex-wrap items-end gap-x-5 gap-y-3">
        <div>
          <p className="text-[0.7rem] uppercase tracking-wide text-fg-subtle">
            {es.dashboard.periodStart}
          </p>
          <p className="font-display text-2xl font-semibold tabular-nums text-fg-muted">
            {money(netStart)}
          </p>
        </div>
        <ArrowRight size={22} className="mb-1.5 shrink-0 text-fg-subtle" />
        <div>
          <p className="text-[0.7rem] uppercase tracking-wide text-fg-subtle">
            {es.dashboard.periodEnd}
          </p>
          <p className="text-gold-gradient font-display text-4xl font-semibold tracking-tight tabular-nums md:text-5xl">
            {money(netEnd)}
          </p>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-fg-muted">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-accent" />
          {es.nav.wallets} <span className="tabular-nums text-fg">{money(s.totalEndMxnCents)}</span>
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
            {t.incomePrevMxnCents > 0 && (
              <span className="text-xs tabular-nums text-fg-subtle">
                {es.dashboard.previously} {money(t.incomePrevMxnCents)}
              </span>
            )}
            <TrendBadge bps={t.incomeTrendBps} />
          </div>
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted">{es.dashboard.expenses}</span>
            <span className="tabular-nums text-fg">{money(t.expenseMxnCents)}</span>
            {t.expensePrevMxnCents > 0 && (
              <span className="text-xs tabular-nums text-fg-subtle">
                {es.dashboard.previously} {money(t.expensePrevMxnCents)}
              </span>
            )}
            <TrendBadge bps={t.expenseTrendBps} goodWhenUp={false} />
          </div>
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

  // Always present so changing the period never adds/removes the widget (which
  // would reflow the grid). Empty periods render a same-size placeholder.
  items.push({
    key: "flow",
    w: 12,
    h: 5,
    minH: 3,
    node: (
      <StatWidget title={es.dashboard.flow}>
        {t && <FlowChart trends={t} />}
      </StatWidget>
    ),
  });
  if ((budgets.data?.length ?? 0) > 0) {
    items.push({ key: "budget", w: 4, h: 5, node: <BudgetWidget period={period} /> });
  }
  // Always present (period-dependent content, fixed size) — see the flow note.
  items.push({
    key: "breakdownExpense",
    w: 4,
    h: 5,
    node: (
      <BreakdownWidget kind="expense" title={es.dashboard.expenseByCategory} period={period} />
    ),
  });
  items.push({
    key: "breakdownIncome",
    w: 4,
    h: 5,
    node: <BreakdownWidget kind="income" title={es.dashboard.incomeByCategory} period={period} />,
  });
  if ((goals.data?.length ?? 0) > 0) {
    items.push({ key: "goals", w: 4, h: 5, node: <GoalsWidget period={period} /> });
  }
  if ((subs.data?.subscriptions.length ?? 0) > 0) {
    items.push({ key: "subscriptions", w: 4, h: 5, node: <SubscriptionsWidget period={period} /> });
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
                labelStyle={{ color: chart.tooltip.color }}
                itemStyle={{ color: chart.tooltip.color }}
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
                labelStyle={{ color: chart.tooltip.color }}
                itemStyle={{ color: chart.tooltip.color }}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </StatWidget>
      ),
    });
  }

  // Income-vs-expense totals (two bars) for the same global period, at the bottom.
  items.push({ key: "flowRange", w: 12, h: 5, minH: 3, node: <FlowRangeWidget period={period} /> });

  return (
    <>
      <PageHeader
        title={es.dashboard.title}
        actions={
          <div className="flex items-center gap-2">
            <PeriodPicker value={period} onChange={setPeriod} />
            <Button variant="ghost" onClick={() => setResetSignal((n) => n + 1)}>
              <span className="flex items-center gap-2">
                <RotateCcw size={15} /> {es.dashboard.resetLayout}
              </span>
            </Button>
          </div>
        }
      />
      <DashboardGrid items={items} resetSignal={resetSignal} />
    </>
  );
}
