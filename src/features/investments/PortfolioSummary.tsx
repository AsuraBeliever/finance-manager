import { useQuery } from "@tanstack/react-query";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { getPortfolio } from "../../lib/api";
import { formatCents } from "../../lib/money";
import { CHART_COLORS, NEGATIVE, POSITIVE, useChartTokens } from "../../lib/palette";
import { es } from "../../i18n/es";

export function PortfolioSummary() {
  const chart = useChartTokens();
  const q = useQuery({ queryKey: ["portfolio"], queryFn: getPortfolio });
  const p = q.data;
  if (!p || p.slices.length === 0) return null;

  const gainPositive = p.totalGainCents >= 0;
  const ret = p.annualizedReturnBps;
  const pie = p.slices
    .filter((s) => s.currentValueCents > 0)
    .map((s, i) => ({
      name: s.name,
      value: s.currentValueCents / 100,
      color: CHART_COLORS[i % CHART_COLORS.length],
    }));

  return (
    <section className="mb-6 grid gap-4 rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card lg:grid-cols-[1fr_auto]">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Metric label={es.investments.portfolioValue} value={formatCents(p.totalValueCents, "MXN")} accent />
        <Metric label={es.investments.portfolioInvested} value={formatCents(p.totalInvestedCents, "MXN")} />
        <Metric
          label={es.investments.portfolioGain}
          value={formatCents(p.totalGainCents, "MXN")}
          tone={gainPositive ? "pos" : "neg"}
        />
        <Metric
          label={es.investments.annualizedReturn}
          value={ret == null ? "—" : `${ret >= 0 ? "+" : ""}${(ret / 100).toFixed(1)}%`}
          tone={ret == null ? undefined : ret >= 0 ? "pos" : "neg"}
        />
      </div>

      {pie.length > 0 && (
        <div className="flex items-center gap-4">
          <ResponsiveContainer width={120} height={120}>
            <PieChart>
              <Pie
                data={pie}
                dataKey="value"
                innerRadius={34}
                outerRadius={56}
                paddingAngle={2}
                stroke="none"
              >
                {pie.map((s) => (
                  <Cell key={s.name} fill={s.color} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={chart.tooltip}
                formatter={(v) => formatCents(Math.round(Number(v) * 100), "MXN")}
              />
            </PieChart>
          </ResponsiveContainer>
          <ul className="space-y-1 text-xs text-fg-muted">
            {pie.slice(0, 5).map((s) => (
              <li key={s.name} className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: s.color }} />
                <span className="truncate">{s.name}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function Metric({
  label,
  value,
  accent,
  tone,
}: {
  label: string;
  value: string;
  accent?: boolean;
  tone?: "pos" | "neg";
}) {
  const color = accent
    ? "text-accent"
    : tone === "pos"
      ? ""
      : tone === "neg"
        ? ""
        : "text-fg";
  return (
    <div>
      <p className="text-xs text-fg-subtle">{label}</p>
      <p
        className={`mt-1 font-display text-lg font-semibold tabular-nums ${color}`}
        style={tone ? { color: tone === "pos" ? POSITIVE : NEGATIVE } : undefined}
      >
        {value}
      </p>
    </div>
  );
}
