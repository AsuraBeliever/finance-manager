import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "../../components/PageHeader";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import {
  getInvestmentCatalog,
  simulateInvestment,
  solveContribution,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS, POSITIVE, useChartTokens } from "../../lib/palette";
import type { SimCadence } from "../../lib/types";
import { es } from "../../i18n/es";

const GOLD = "#c9a14a";
type Mode = "project" | "goal" | "compare";
const CADENCES: SimCadence[] = ["monthly", "biweekly", "weekly", "none"];

const rateToBps = (s: string) => {
  const n = Number(s);
  return Number.isFinite(n) ? Math.round(n * 100) : 0;
};
const yearsToMonths = (s: string) => {
  const n = Number(s);
  return Number.isFinite(n) ? Math.round(n * 12) : 0;
};

export function SimulatorPage() {
  const [mode, setMode] = useState<Mode>("project");
  const [initial, setInitial] = useState("10000");
  const [contribution, setContribution] = useState("1000");
  const [cadence, setCadence] = useState<SimCadence>("monthly");
  const [rate, setRate] = useState("10");
  const [years, setYears] = useState("5");

  const shared = { initial, setInitial, years, setYears };

  return (
    <>
      <Link
        to="/inversiones"
        className="mb-3 inline-flex items-center gap-1.5 text-sm text-fg-subtle transition-colors hover:text-fg"
      >
        <ArrowLeft size={15} /> {es.simulator.back}
      </Link>
      <PageHeader title={es.simulator.subtitle} />

      <div className="mb-5 inline-flex rounded-xl border border-border-muted bg-surface-raised p-1">
        {(["project", "goal", "compare"] as Mode[]).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            className={`rounded-lg px-4 py-1.5 text-sm font-medium transition-colors ${
              mode === m ? "bg-accent/15 text-accent" : "text-fg-muted hover:text-fg"
            }`}
          >
            {m === "project"
              ? es.simulator.modeProject
              : m === "goal"
                ? es.simulator.modeGoal
                : es.simulator.modeCompare}
          </button>
        ))}
      </div>

      {mode === "project" && (
        <ProjectMode
          {...shared}
          contribution={contribution}
          setContribution={setContribution}
          cadence={cadence}
          setCadence={setCadence}
          rate={rate}
          setRate={setRate}
        />
      )}
      {mode === "goal" && <GoalMode {...shared} rate={rate} setRate={setRate} />}
      {mode === "compare" && (
        <CompareMode
          {...shared}
          contribution={contribution}
          setContribution={setContribution}
          cadence={cadence}
          setCadence={setCadence}
        />
      )}
    </>
  );
}

// ---- shared bits ----

function MoneyField({
  label,
  value,
  onChange,
  money = true,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  /** Money fields get the $ + thousands formatting; plain ones (years) don't. */
  money?: boolean;
}) {
  return (
    <Field label={label}>
      {money ? (
        <MoneyInput value={value} onChange={onChange} />
      ) : (
        <input
          className={inputClass}
          inputMode="decimal"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
    </Field>
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

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card">
      {children}
    </div>
  );
}

interface SharedProps {
  initial: string;
  setInitial: (v: string) => void;
  years: string;
  setYears: (v: string) => void;
}

// ---- mode: projection ("¿cuánto crecería?") ----

function ProjectMode({
  initial,
  setInitial,
  years,
  setYears,
  contribution,
  setContribution,
  cadence,
  setCadence,
  rate,
  setRate,
}: SharedProps & {
  contribution: string;
  setContribution: (v: string) => void;
  cadence: SimCadence;
  setCadence: (v: SimCadence) => void;
  rate: string;
  setRate: (v: string) => void;
}) {
  const chart = useChartTokens();
  const input = useMemo(
    () => ({
      initialCents: parseToCents(initial) ?? 0,
      contributionCents: parseToCents(contribution) ?? 0,
      cadence,
      annualRateBps: rateToBps(rate),
      months: yearsToMonths(years),
    }),
    [initial, contribution, cadence, rate, years],
  );
  const valid = input.months > 0 && input.months <= 1200;
  const sim = useQuery({
    queryKey: ["simulate", input],
    queryFn: () => simulateInvestment(input),
    enabled: valid,
    placeholderData: (p) => p,
  });
  const data = sim.data;
  const chartData = (data?.points ?? []).map((p) => ({
    month: p.month,
    contributed: p.contributedCents / 100,
    interest: Math.max(0, (p.valueCents - p.contributedCents) / 100),
  }));
  const rateNum = Number(rate);
  const doubling = rateNum > 0 ? (72 / rateNum).toFixed(1) : null;

  return (
    <div className="grid gap-5 lg:grid-cols-[20rem_1fr]">
      <Card>
        <div className="space-y-4">
          <MoneyField label={es.simulator.initial} value={initial} onChange={setInitial} />
          <MoneyField
            label={es.simulator.contribution}
            value={contribution}
            onChange={setContribution}
          />
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
          <RateField rate={rate} setRate={setRate} />
          <MoneyField label={es.simulator.years} value={years} onChange={setYears} money={false} />
          {doubling && (
            <p className="text-xs text-fg-subtle">
              {es.simulator.doublesIn.replace("{years}", doubling)}
            </p>
          )}
        </div>
      </Card>

      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Stat label={es.simulator.finalValue} value={data?.finalValueCents} accent />
          <Stat label={es.simulator.totalContributed} value={data?.totalContributedCents} />
          <Stat label={es.simulator.totalInterest} value={data?.totalInterestCents} gold />
        </div>
        <Card>
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
        </Card>
      </div>
    </div>
  );
}

// ---- mode: goal ("¿cuánto debo aportar para llegar a $X?") ----

function GoalMode({
  initial,
  setInitial,
  years,
  setYears,
  rate,
  setRate,
}: SharedProps & { rate: string; setRate: (v: string) => void }) {
  const [target, setTarget] = useState("100000");
  const input = useMemo(
    () => ({
      initialCents: parseToCents(initial) ?? 0,
      targetCents: parseToCents(target) ?? 0,
      annualRateBps: rateToBps(rate),
      months: yearsToMonths(years),
    }),
    [initial, target, rate, years],
  );
  const valid = input.months > 0 && input.targetCents > 0;
  const solve = useQuery({
    queryKey: ["solve", input],
    queryFn: () => solveContribution(input),
    enabled: valid,
    placeholderData: (p) => p,
  });
  const monthly = solve.data?.monthlyContributionCents;

  return (
    <div className="grid gap-5 lg:grid-cols-[20rem_1fr]">
      <Card>
        <div className="space-y-4">
          <MoneyField label={es.simulator.goalTarget} value={target} onChange={setTarget} />
          <MoneyField label={es.simulator.initial} value={initial} onChange={setInitial} />
          <RateField rate={rate} setRate={setRate} />
          <MoneyField label={es.simulator.years} value={years} onChange={setYears} money={false} />
        </div>
      </Card>
      <div className="space-y-4">
        <div className="rounded-2xl border border-border-muted bg-surface-raised p-6 shadow-card">
          <p className="text-sm text-fg-muted">{es.simulator.goalNeed}</p>
          <p className="mt-1 font-display text-4xl font-semibold tabular-nums text-accent">
            {monthly === undefined ? "—" : formatCents(monthly, "MXN")}
          </p>
          {monthly === 0 && (
            <p className="mt-2 text-sm text-fg-subtle">{es.simulator.goalReached}</p>
          )}
        </div>
      </div>
    </div>
  );
}

// ---- mode: compare ----

interface Instrument {
  key: string;
  name: string;
  rate: string;
  color: string;
}

function CompareMode({
  initial,
  setInitial,
  years,
  setYears,
  contribution,
  setContribution,
  cadence,
  setCadence,
}: SharedProps & {
  contribution: string;
  setContribution: (v: string) => void;
  cadence: SimCadence;
  setCadence: (v: SimCadence) => void;
}) {
  const chart = useChartTokens();
  const catalog = useQuery({ queryKey: ["investmentCatalog"], queryFn: getInvestmentCatalog });
  const [instruments, setInstruments] = useState<Instrument[]>([
    { key: "nu", name: "Nu", rate: "15", color: CHART_COLORS[0] },
    { key: "cetes", name: "CETES", rate: "10", color: CHART_COLORS[1] },
    { key: "bonddia", name: "BONDDIA", rate: "6.5", color: CHART_COLORS[2] },
  ]);

  // Prefill the real Banxico rates once, when the catalog arrives.
  const prefilled = useRef(false);
  useEffect(() => {
    if (prefilled.current || !catalog.data) return;
    const byId = (id: string) =>
      catalog.data!.find((c) => c.id === id)?.rateBps ?? null;
    const cetes = byId("cetes_91");
    const bonddia = byId("bonddia");
    setInstruments((prev) =>
      prev.map((i) =>
        i.key === "cetes" && cetes != null
          ? { ...i, rate: (cetes / 100).toString() }
          : i.key === "bonddia" && bonddia != null
            ? { ...i, rate: (bonddia / 100).toString() }
            : i,
      ),
    );
    prefilled.current = true;
  }, [catalog.data]);

  const base = {
    initialCents: parseToCents(initial) ?? 0,
    contributionCents: parseToCents(contribution) ?? 0,
    cadence,
    months: yearsToMonths(years),
  };
  const valid = base.months > 0 && base.months <= 1200;
  const ratesKey = instruments.map((i) => i.rate).join(",");
  const sims = useQuery({
    queryKey: ["compare", base, ratesKey],
    enabled: valid,
    placeholderData: (p) => p,
    queryFn: async () => {
      const results = await Promise.all(
        instruments.map((i) =>
          simulateInvestment({ ...base, annualRateBps: rateToBps(i.rate) }),
        ),
      );
      return instruments.map((i, idx) => ({ ...i, result: results[idx] }));
    },
  });

  const series = sims.data;
  const chartData = useMemo(() => {
    if (!series || series.length === 0) return [];
    const len = series[0].result.points.length;
    return Array.from({ length: len }, (_, idx) => {
      const row: Record<string, number> = { month: series[0].result.points[idx].month };
      for (const s of series) row[s.key] = s.result.points[idx].valueCents / 100;
      return row;
    });
  }, [series]);

  return (
    <div className="grid gap-5 lg:grid-cols-[20rem_1fr]">
      <Card>
        <div className="space-y-4">
          <MoneyField label={es.simulator.initial} value={initial} onChange={setInitial} />
          <MoneyField
            label={es.simulator.contribution}
            value={contribution}
            onChange={setContribution}
          />
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
          <MoneyField label={es.simulator.years} value={years} onChange={setYears} money={false} />
          <div className="space-y-2 pt-1">
            <p className="text-[0.8rem] font-medium text-fg-muted">{es.simulator.presets}</p>
            {instruments.map((inst, idx) => (
              <div key={inst.key} className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: inst.color }} />
                <span className="w-20 truncate text-sm text-fg">{inst.name}</span>
                <input
                  className={inputClass + " flex-1"}
                  inputMode="decimal"
                  value={inst.rate}
                  onChange={(e) =>
                    setInstruments((prev) =>
                      prev.map((p, i) => (i === idx ? { ...p, rate: e.target.value } : p)),
                    )
                  }
                />
                <span className="text-xs text-fg-subtle">%</span>
              </div>
            ))}
            <p className="text-xs text-fg-subtle">{es.simulator.compareHint}</p>
          </div>
        </div>
      </Card>

      <div className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {(series ?? []).map((s) => (
            <div
              key={s.key}
              className="rounded-2xl border border-border-muted bg-surface-raised p-4 shadow-card"
            >
              <p className="flex items-center gap-1.5 text-xs text-fg-subtle">
                <span className="h-2 w-2 rounded-sm" style={{ backgroundColor: s.color }} />
                {s.name} · {s.rate}%
              </p>
              <p className="mt-1 font-display text-lg font-semibold tabular-nums text-fg">
                {formatCents(s.result.finalValueCents, "MXN")}
              </p>
            </div>
          ))}
        </div>
        <Card>
          <h3 className="mb-4 font-display text-lg font-medium tracking-tight text-fg">
            {es.simulator.compareTitle}
          </h3>
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={chartData}>
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
                formatter={(v, key) => [
                  formatCents(Math.round(Number(v) * 100), "MXN"),
                  instruments.find((i) => i.key === key)?.name ?? key,
                ]}
                labelFormatter={(m) => `${(Number(m) / 12).toFixed(1)} ${es.simulator.years}`}
              />
              {instruments.map((inst) => (
                <Line
                  key={inst.key}
                  type="monotone"
                  dataKey={inst.key}
                  stroke={inst.color}
                  strokeWidth={2}
                  dot={false}
                  isAnimationActive={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </Card>
      </div>
    </div>
  );
}

function RateField({ rate, setRate }: { rate: string; setRate: (v: string) => void }) {
  const presets = [
    { label: "Nu", rate: 15 },
    { label: "CETES", rate: 10 },
    { label: "BONDDIA", rate: 6.5 },
  ];
  return (
    <>
      <Field label={es.simulator.rate}>
        <input
          className={inputClass}
          inputMode="decimal"
          value={rate}
          onChange={(e) => setRate(e.target.value)}
        />
      </Field>
      <div className="flex flex-wrap gap-2">
        {presets.map((p) => (
          <button
            key={p.label}
            onClick={() => setRate(String(p.rate))}
            className="rounded-full border border-border-muted px-3 py-1 text-xs text-fg-muted transition-colors hover:border-accent hover:text-accent"
          >
            {p.label} {p.rate}%
          </button>
        ))}
      </div>
    </>
  );
}
