import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { Field, inputClass } from "../../components/Field";
import { simulateInvestment } from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { POSITIVE, useChartTokens } from "../../lib/palette";
import type { SimCadence } from "../../lib/types";
import { es } from "../../i18n/es";

const GOLD = "#c9a14a";

// Approximate current rates for MXN instruments, as quick presets. Real
// per-instrument rates are wired into the comparator in a later milestone.
const RATE_PRESETS: { key: string; label: string; rate: number }[] = [
  { key: "nu", label: "Nu", rate: 15 },
  { key: "cetes", label: "CETES", rate: 10 },
  { key: "bonddia", label: "BONDDIA", rate: 6.5 },
];

const CADENCES: SimCadence[] = ["monthly", "biweekly", "weekly", "none"];

export function SimulatorPage() {
  const chart = useChartTokens();
  const [initial, setInitial] = useState("10000");
  const [contribution, setContribution] = useState("1000");
  const [cadence, setCadence] = useState<SimCadence>("monthly");
  const [rate, setRate] = useState("10");
  const [years, setYears] = useState("5");

  const input = useMemo(() => {
    const initialCents = parseToCents(initial) ?? 0;
    const contributionCents = parseToCents(contribution) ?? 0;
    const rateNum = Number(rate);
    const yearsNum = Number(years);
    return {
      initialCents,
      contributionCents,
      cadence,
      annualRateBps: Number.isFinite(rateNum) ? Math.round(rateNum * 100) : 0,
      months: Number.isFinite(yearsNum) ? Math.round(yearsNum * 12) : 0,
    };
  }, [initial, contribution, cadence, rate, years]);

  const valid = input.months > 0 && input.months <= 1200;
  const sim = useQuery({
    queryKey: ["simulate", input],
    queryFn: () => simulateInvestment(input),
    enabled: valid,
    placeholderData: (prev) => prev,
  });

  const data = sim.data;
  const chartData = useMemo(
    () =>
      (data?.points ?? []).map((p) => ({
        month: p.month,
        contributed: p.contributedCents / 100,
        interest: Math.max(0, (p.valueCents - p.contributedCents) / 100),
      })),
    [data],
  );

  const rateNum = Number(rate);
  const doublingYears =
    Number.isFinite(rateNum) && rateNum > 0 ? (72 / rateNum).toFixed(1) : null;

  return (
    <>
      <Link
        to="/inversiones"
        className="mb-3 inline-flex items-center gap-1.5 text-sm text-fg-subtle transition-colors hover:text-fg"
      >
        <ArrowLeft size={15} /> {es.simulator.back}
      </Link>
      <PageHeader title={es.simulator.subtitle} />
      <p className="mb-5 max-w-2xl text-sm text-fg-muted">{es.simulator.description}</p>

      <div className="grid gap-5 lg:grid-cols-[20rem_1fr]">
        {/* Controls */}
        <section className="space-y-4 rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card">
          <Field label={es.simulator.initial}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={initial}
              onChange={(e) => setInitial(e.target.value)}
            />
          </Field>
          <Field label={es.simulator.contribution}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={contribution}
              onChange={(e) => setContribution(e.target.value)}
            />
          </Field>
          <Field label={es.simulator.cadence}>
            <select
              className={inputClass}
              value={cadence}
              onChange={(e) => setCadence(e.target.value as SimCadence)}
            >
              {CADENCES.map((c) => (
                <option key={c} value={c}>
                  {es.simulator.cadenceOptions[c]}
                </option>
              ))}
            </select>
          </Field>
          <Field label={es.simulator.rate}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={rate}
              onChange={(e) => setRate(e.target.value)}
            />
          </Field>
          <div className="flex flex-wrap gap-2">
            {RATE_PRESETS.map((p) => (
              <button
                key={p.key}
                onClick={() => setRate(String(p.rate))}
                className="rounded-full border border-border-muted px-3 py-1 text-xs text-fg-muted transition-colors hover:border-accent hover:text-accent"
              >
                {p.label} {p.rate}%
              </button>
            ))}
          </div>
          <Field label={es.simulator.years}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={years}
              onChange={(e) => setYears(e.target.value)}
            />
          </Field>
          {doublingYears && (
            <p className="text-xs text-fg-subtle">
              {es.simulator.doublesIn.replace("{years}", doublingYears)}
            </p>
          )}
        </section>

        {/* Results */}
        <section className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Stat label={es.simulator.finalValue} value={data?.finalValueCents} accent />
            <Stat label={es.simulator.totalContributed} value={data?.totalContributedCents} />
            <Stat label={es.simulator.totalInterest} value={data?.totalInterestCents} gold />
          </div>

          <div className="rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card">
            <h3 className="mb-4 font-display text-lg font-medium tracking-tight text-fg">
              {es.simulator.growthTitle}
            </h3>
            <ResponsiveContainer width="100%" height={320}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="simContrib" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={GOLD} stopOpacity={0.5} />
                    <stop offset="100%" stopColor={GOLD} stopOpacity={0.08} />
                  </linearGradient>
                  <linearGradient id="simInterest" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={POSITIVE} stopOpacity={0.55} />
                    <stop offset="100%" stopColor={POSITIVE} stopOpacity={0.1} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={chart.grid} strokeDasharray="3 3" />
                <XAxis
                  dataKey="month"
                  stroke={chart.axis}
                  fontSize={11}
                  minTickGap={28}
                  tickFormatter={(m) => (Number(m) % 12 === 0 ? `${Number(m) / 12}a` : "")}
                />
                <YAxis
                  stroke={chart.axis}
                  fontSize={11}
                  tickFormatter={(v) => (Number(v) / 1000).toFixed(0) + "k"}
                />
                <Tooltip
                  contentStyle={chart.tooltip}
                  formatter={(v, name) => [
                    formatCents(Math.round(Number(v) * 100), "MXN"),
                    name === "contributed"
                      ? es.simulator.contributedSeries
                      : es.simulator.interestSeries,
                  ]}
                  labelFormatter={(m) => `${(Number(m) / 12).toFixed(1)} ${es.simulator.years}`}
                />
                <Area
                  type="monotone"
                  dataKey="contributed"
                  stackId="1"
                  stroke={GOLD}
                  fill="url(#simContrib)"
                  strokeWidth={2}
                />
                <Area
                  type="monotone"
                  dataKey="interest"
                  stackId="1"
                  stroke={POSITIVE}
                  fill="url(#simInterest)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
            <div className="mt-3 flex items-center gap-4 text-xs text-fg-subtle">
              <Legend color={GOLD} label={es.simulator.contributedSeries} />
              <Legend color={POSITIVE} label={es.simulator.interestSeries} />
            </div>
          </div>
        </section>
      </div>
    </>
  );
}

function Stat({
  label,
  value,
  accent,
  gold,
}: {
  label: string;
  value: number | undefined;
  accent?: boolean;
  gold?: boolean;
}) {
  return (
    <div className="rounded-2xl border border-border-muted bg-surface-raised p-4 shadow-card">
      <p className="text-xs text-fg-subtle">{label}</p>
      <p
        className={`mt-1 font-display text-xl font-semibold tabular-nums ${
          accent ? "text-accent" : gold ? "text-gold" : "text-fg"
        }`}
      >
        {value === undefined ? "—" : formatCents(value, "MXN")}
      </p>
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
