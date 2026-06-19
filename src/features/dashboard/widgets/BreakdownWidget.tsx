import { useQuery } from "@tanstack/react-query";
import { Cell, Pie, PieChart, ResponsiveContainer } from "recharts";
import { StatWidget } from "../../../components/StatWidget";
import { getCategoryBreakdown } from "../../../lib/api";
import { formatCents } from "../../../lib/money";
import { CHART_COLORS } from "../../../lib/palette";
import { es } from "../../../i18n/es";
import { seedName } from "../../../i18n/seed";

/** Donut + ranked category list for income or expense (current month). */
export function BreakdownWidget({
  kind,
  title,
}: {
  kind: "income" | "expense";
  title: string;
}) {
  const q = useQuery({
    queryKey: ["breakdown", kind],
    queryFn: () => getCategoryBreakdown(kind, "month"),
  });

  const data = q.data;
  if (!data || data.slices.length === 0) return null;

  const total = data.totalMxnCents;
  const colorFor = (i: number, c: string | null) => c ?? CHART_COLORS[i % CHART_COLORS.length];
  const donut = data.slices.map((s, i) => ({
    name: s.name,
    value: s.mxnCents / 100,
    color: colorFor(i, s.color),
  }));

  return (
    <StatWidget title={title}>
      <div className="flex flex-wrap items-center gap-5">
        <div className="relative h-36 w-36 shrink-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={donut}
                dataKey="value"
                nameKey="name"
                innerRadius={48}
                outerRadius={68}
                paddingAngle={2}
                stroke="none"
              >
                {donut.map((d) => (
                  <Cell key={d.name} fill={d.color} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-[0.7rem] text-fg-subtle">{es.dashboard.total}</span>
            <span className="font-display text-sm font-semibold tabular-nums text-fg">
              {formatCents(total, "MXN")}
            </span>
          </div>
        </div>

        <ul className="min-w-[10rem] flex-1 space-y-2">
          {data.slices.map((s, i) => {
            const pct = total > 0 ? Math.round((s.mxnCents / total) * 100) : 0;
            return (
              <li key={s.categoryId ?? "none"} className="flex items-center gap-2 text-sm">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: colorFor(i, s.color) }}
                />
                <span className="truncate text-fg-muted">{seedName(s.name)}</span>
                <span className="ml-auto tabular-nums text-fg">
                  {formatCents(s.mxnCents, "MXN")}
                </span>
                <span className="w-9 shrink-0 text-right text-xs tabular-nums text-fg-subtle">
                  {pct}%
                </span>
              </li>
            );
          })}
        </ul>
      </div>
    </StatWidget>
  );
}
