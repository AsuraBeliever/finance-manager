import { Bar, BarChart, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCents } from "../../lib/money";
import { NEGATIVE, POSITIVE, useChartTokens } from "../../lib/palette";
import type { BucketUnit, SpendingTrends } from "../../lib/types";
import { es } from "../../i18n/es";
import { getLocale } from "../../i18n/store";

const intlLocale = () => (getLocale() === "en" ? "en-US" : "es-MX");
const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

// Label one flow bar. `key` is 'YYYY-MM-DD' (daily) or 'YYYY-MM' (monthly);
// the compact form feeds the X axis, the long form the tooltip.
export function bucketLabel(key: string, unit: BucketUnit, long: boolean): string {
  const fmt = (opts: Intl.DateTimeFormatOptions, date: Date) =>
    new Intl.DateTimeFormat(intlLocale(), opts).format(date);
  if (unit === "month") {
    const [y, m] = key.split("-").map(Number);
    const date = new Date(y, m - 1, 1);
    return cap(
      fmt(long ? { month: "long", year: "numeric" } : { month: "short", year: "2-digit" }, date),
    );
  }
  const date = new Date(`${key}T00:00:00`);
  return cap(
    fmt(
      long ? { weekday: "long", day: "numeric", month: "long", year: "numeric" } : { day: "2-digit" },
      date,
    ),
  );
}

/** Adaptive income/expense bar chart over a resolved period (daily or monthly
 *  buckets). Shared by the period-driven dashboard chart and the standalone
 *  range chart. */
export function FlowChart({ trends }: { trends: SpendingTrends }) {
  const chart = useChartTokens();
  const unit = trends.bucketUnit;

  // Keep the widget the exact same size when the chosen period is empty: show a
  // centered note instead of collapsing (collapsing would reflow the grid).
  if (trends.buckets.length === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-fg-subtle">{es.dashboard.noPeriodData}</p>
      </div>
    );
  }

  const data = trends.buckets.map((b) => ({
    key: b.key,
    [es.dashboard.incomes]: b.incomeMxnCents / 100,
    [es.dashboard.expenses]: b.expenseMxnCents / 100,
  }));

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data}>
        <XAxis
          dataKey="key"
          stroke={chart.axis}
          fontSize={11}
          tickFormatter={(k) => bucketLabel(String(k), unit, false)}
        />
        <YAxis stroke={chart.axis} fontSize={11} width={40} />
        <Tooltip
          labelFormatter={(label) => bucketLabel(String(label), unit, true)}
          formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
          contentStyle={chart.tooltip}
          labelStyle={{ color: chart.tooltip.color }}
          cursor={{ fill: "color-mix(in oklab, var(--color-border-muted) 40%, transparent)" }}
        />
        <Legend />
        <Bar dataKey={es.dashboard.incomes} fill={POSITIVE} radius={[3, 3, 0, 0]} />
        <Bar dataKey={es.dashboard.expenses} fill={NEGATIVE} radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Two adjacent bars — total income vs total expense — over the selected period.
 *  A grouped (touching) pair like the classic overview chart; the per-bucket
 *  breakdown lives in the time-series FlowChart. */
export function FlowTotalsChart({ trends }: { trends: SpendingTrends }) {
  const chart = useChartTokens();

  if (trends.incomeMxnCents === 0 && trends.expenseMxnCents === 0) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-fg-subtle">{es.dashboard.noPeriodData}</p>
      </div>
    );
  }

  // A single category with two series → the bars render side by side; barGap 0
  // makes them touch.
  const data = [
    {
      label: " ",
      [es.dashboard.incomes]: trends.incomeMxnCents / 100,
      [es.dashboard.expenses]: trends.expenseMxnCents / 100,
    },
  ];

  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} barGap={0} barCategoryGap="22%">
        <XAxis dataKey="label" stroke={chart.axis} fontSize={12} tickLine={false} />
        <YAxis stroke={chart.axis} fontSize={11} width={40} />
        <Tooltip
          formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
          contentStyle={chart.tooltip}
          labelStyle={{ color: chart.tooltip.color }}
          cursor={{ fill: "color-mix(in oklab, var(--color-border-muted) 40%, transparent)" }}
        />
        <Legend />
        <Bar dataKey={es.dashboard.incomes} fill={POSITIVE} radius={[4, 4, 0, 0]} />
        <Bar dataKey={es.dashboard.expenses} fill={NEGATIVE} radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
