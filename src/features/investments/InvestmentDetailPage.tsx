import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Lock,
  LockOpen,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import {
  addInvestmentMovement,
  addSnapshot,
  closeInvestment,
  deleteInvestment,
  deleteInvestmentMovement,
  getExchangeRates,
  getInvestmentDetail,
  listWallets,
  projectInvestment,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { todayIso } from "../../lib/date";
import { POSITIVE, useChartTokens } from "../../lib/palette";
import type { SimCadence } from "../../lib/types";
import { es } from "../../i18n/es";
import { InvestmentFormModal } from "./InvestmentFormModal";

const SIM_GOLD = "#c9a14a";
const SIM_CADENCES: SimCadence[] = ["monthly", "biweekly", "weekly", "none"];
// Projection time range (years): +/- steppers or a free manual value, capped at
// 50 (the backend clamps months to 600).
const MIN_YEARS = 1;
const MAX_YEARS = 50;
const clampYears = (n: number) => Math.min(MAX_YEARS, Math.max(MIN_YEARS, n));

export function InvestmentDetailPage() {
  const { id } = useParams();
  const chart = useChartTokens();
  const invId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);
  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotValue, setSnapshotValue] = useState("");
  const [snapshotDate, setSnapshotDate] = useState(todayIso());
  const [snapshotError, setSnapshotError] = useState<string | null>(null);
  const [movementKind, setMovementKind] = useState<"deposit" | "withdrawal" | null>(null);
  const [movementValue, setMovementValue] = useState("");
  const [movementDate, setMovementDate] = useState(todayIso());
  const [movementWalletId, setMovementWalletId] = useState<number | null>(null);
  const [movementError, setMovementError] = useState<string | null>(null);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [movementToDelete, setMovementToDelete] = useState<number | null>(null);

  const detail = useQuery({
    queryKey: ["investments", invId],
    queryFn: () => getInvestmentDetail(invId),
    enabled: Number.isFinite(invId),
  });

  const fxRates = useQuery({ queryKey: ["exchangeRates"], queryFn: getExchangeRates });
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["investments"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    // Movements can move money in/out of a wallet, so refresh balances + ledger.
    queryClient.invalidateQueries({ queryKey: ["wallets"] });
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
  };

  const close = useMutation({
    mutationFn: (closed: boolean) => closeInvestment(invId, closed),
    onSuccess: invalidate,
  });

  const remove = useMutation({
    mutationFn: () => deleteInvestment(invId),
    onSuccess: () => {
      invalidate();
      navigate("/inversiones");
    },
  });

  const snapshot = useMutation({
    mutationFn: () => {
      const cents = parseToCents(snapshotValue);
      if (cents === null || cents < 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      return addSnapshot(invId, cents, snapshotDate);
    },
    onSuccess: () => {
      invalidate();
      setSnapshotOpen(false);
      setSnapshotValue("");
      setSnapshotDate(todayIso());
    },
    onError: (e) => setSnapshotError(e instanceof Error ? e.message : String(e)),
  });

  const movement = useMutation({
    mutationFn: () => {
      const cents = parseToCents(movementValue);
      if (cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      if (!movementKind) return Promise.reject(new Error(es.investments.invalidAmount));
      return addInvestmentMovement(invId, movementKind, cents, movementDate, movementWalletId);
    },
    onSuccess: () => {
      invalidate();
      setMovementKind(null);
      setMovementValue("");
      setMovementDate(todayIso());
    },
    onError: (e) => setMovementError(e instanceof Error ? e.message : String(e)),
  });

  const removeMovement = useMutation({
    mutationFn: deleteInvestmentMovement,
    onSuccess: invalidate,
  });

  // Projection controls. By default it shows the plain projection (the money
  // untouched). A toggle opens the "what-if" (recurring contributions), and the
  // zoom stretches/shrinks the time range. The same RPC serves both — with
  // contribution 0 it's just the plain forward projection over the zoom range.
  const [simEnabled, setSimEnabled] = useState(false);
  const [simContribution, setSimContribution] = useState("");
  const [simCadence, setSimCadence] = useState<SimCadence>("monthly");
  const [zoomYears, setZoomYears] = useState("5");
  const parsedYears = Math.round(Number(zoomYears));
  const zoomYearsNum = parsedYears > 0 ? clampYears(parsedYears) : MIN_YEARS;
  const stepZoom = (delta: number) => setZoomYears(String(clampYears(zoomYearsNum + delta)));
  const simContributionCents = simEnabled ? (parseToCents(simContribution) ?? 0) : 0;
  const simActive = simContributionCents > 0;
  const projectionQuery = useQuery({
    queryKey: ["projectInvestment", invId, simContributionCents, simCadence, zoomYearsNum],
    queryFn: () =>
      projectInvestment({
        id: invId,
        contributionCents: simContributionCents,
        cadence: simCadence,
        months: zoomYearsNum * 12,
      }),
    enabled: Number.isFinite(invId),
    placeholderData: (p) => p,
  });

  if (detail.isPending) return <p className="text-sm text-fg-subtle">{es.common.loading}</p>;
  if (detail.isError) return <p className="text-sm text-danger">{String(detail.error)}</p>;

  const d = detail.data;
  // Open the deposit/withdrawal modal with the investment's remembered wallet
  // preselected (the user can still change or clear it).
  const openMovement = (kind: "deposit" | "withdrawal") => {
    setMovementWalletId(d.linkedWalletId);
    setMovementValue("");
    setMovementDate(todayIso());
    setMovementError(null);
    setMovementKind(kind);
  };
  const gainPositive = d.gainCents >= 0;
  const todayStr = todayIso();
  // The chart always comes from the projection RPC (zoom + optional contribs);
  // the detail's own projection is just the first-paint fallback. With the
  // what-if on, the future line is gold ("con aportes"); otherwise it's the
  // plain green dashed projection. The solid "actual" past line is the same.
  const projData = projectionQuery.data;
  const activeProjection = projData?.projection ?? d.projection;
  const chartData = activeProjection.map((p) => ({
    date: p.date,
    actual: p.date <= todayStr ? p.valueCents / 100 : null,
    projected: !simActive && p.date >= todayStr ? p.valueCents / 100 : null,
    withContrib: simActive && p.date >= todayStr ? p.valueCents / 100 : null,
  }));

  // crypto extras: quantity from params + USD equivalent via the USD fx rate
  let cryptoParams: { symbol: string; quantity_e8: number } | null = null;
  if (d.calculator === "crypto") {
    try {
      cryptoParams = JSON.parse(d.paramsJson);
    } catch {
      cryptoParams = null;
    }
  }
  const usdRateMicros = fxRates.data?.find((r) => r.currencyCode === "USD")
    ?.rateToMxnMicros;
  const usdEquivalent =
    cryptoParams && usdRateMicros && d.currencyCode === "MXN"
      ? Math.round((d.currentValueCents * 1_000_000) / usdRateMicros)
      : null;

  return (
    <>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-semibold tracking-tight">{d.name}</h2>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => setEditOpen(true)}>
            <span className="flex items-center gap-2">
              <Pencil size={15} /> {es.common.edit}
            </span>
          </Button>
          <Button
            variant="ghost"
            onClick={() => close.mutate(!d.isClosed)}
            disabled={close.isPending}
          >
            <span className="flex items-center gap-2">
              {d.isClosed ? <LockOpen size={15} /> : <Lock size={15} />}
              {d.isClosed ? es.investments.reopen : es.investments.close}
            </span>
          </Button>
          <Button variant="danger" onClick={() => setConfirmDeleteOpen(true)}>
            <span className="flex items-center gap-2">
              <Trash2 size={15} /> {es.common.delete}
            </span>
          </Button>
        </div>
      </div>

      <div className="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-fg-subtle">{es.investments.currentValue}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">
            {formatCents(d.currentValueCents, d.currencyCode)}
          </p>
          {usdEquivalent !== null && (
            <p className="mt-0.5 text-xs tabular-nums text-fg-subtle">
              ≈ {formatCents(usdEquivalent, "USD")}
            </p>
          )}
        </div>
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-fg-subtle">{es.investments.gain}</p>
          <p
            className={`mt-1 text-xl font-semibold tabular-nums ${
              gainPositive ? "text-accent" : "text-danger"
            }`}
          >
            {gainPositive ? "+" : ""}
            {formatCents(d.gainCents, d.currencyCode)}
          </p>
        </div>
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-fg-subtle">{es.investments.netInvested}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">
            {formatCents(d.netInvestedCents, d.currencyCode)}
          </p>
        </div>
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          {cryptoParams ? (
            <>
              <p className="text-xs text-fg-subtle">{es.investments.quantity}</p>
              <p className="mt-1 text-xl font-semibold tabular-nums">
                {(cryptoParams.quantity_e8 / 1e8).toString()} {cryptoParams.symbol}
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-fg-subtle">
                {d.maturityDate ? es.investments.maturity : es.investments.startDate}
              </p>
              <p className="mt-1 text-xl font-semibold">{d.maturityDate ?? d.startDate}</p>
            </>
          )}
        </div>
      </div>

      {d.calculator !== "crypto" && (
      <section className="mb-4 rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-baseline gap-2">
            <h3 className="font-display text-lg font-medium tracking-tight text-fg">
              {es.investments.projection}
            </h3>
            {projData?.annualRateBps != null && (
              <span className="text-xs text-fg-subtle">
                {es.investments.projectionAtRate.replace(
                  "{rate}",
                  (projData.annualRateBps / 100).toFixed(2),
                )}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            {/* Zoom: stretch/shrink the projection time range (steppers or a
                manual value). */}
            <div className="flex items-center gap-0.5 rounded-lg border border-border-muted p-0.5">
              <button
                onClick={() => stepZoom(-1)}
                disabled={zoomYearsNum <= MIN_YEARS}
                aria-label="zoom in"
                className="rounded-md p-1 text-fg-muted transition-colors hover:bg-surface-overlay hover:text-fg disabled:opacity-30"
              >
                <ZoomIn size={15} />
              </button>
              <input
                value={zoomYears}
                onChange={(e) => setZoomYears(e.target.value)}
                onBlur={() => setZoomYears(String(zoomYearsNum))}
                inputMode="numeric"
                aria-label="años"
                className="w-8 bg-transparent text-center text-xs tabular-nums text-fg outline-none"
              />
              <span className="pr-1 text-xs text-fg-subtle">
                {es.investments.projectionYearsShort}
              </span>
              <button
                onClick={() => stepZoom(1)}
                disabled={zoomYearsNum >= MAX_YEARS}
                aria-label="zoom out"
                className="rounded-md p-1 text-fg-muted transition-colors hover:bg-surface-overlay hover:text-fg disabled:opacity-30"
              >
                <ZoomOut size={15} />
              </button>
            </div>
            {/* Toggle the what-if (recurring contributions). */}
            <button
              onClick={() => setSimEnabled((v) => !v)}
              className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors ${
                simEnabled
                  ? "border-accent/40 bg-accent/15 text-accent"
                  : "border-border-muted text-fg-muted hover:text-fg"
              }`}
            >
              <SlidersHorizontal size={15} /> {es.investments.projectionSimToggle}
            </button>
          </div>
        </div>

        {/* What-if controls: simulate adding money straight on this chart. */}
        {simEnabled && (
          <div className="mb-4 grid grid-cols-2 gap-3">
            <Field label={es.investments.projectionContribution}>
              <MoneyInput value={simContribution} onChange={setSimContribution} />
            </Field>
            <Field label={es.simulator.cadence}>
              <select
                className={inputClass}
                value={simCadence}
                onChange={(e) => setSimCadence(e.target.value as SimCadence)}
              >
                {SIM_CADENCES.map((c) => (
                  <option key={c} value={c}>
                    {es.simulator.cadenceOptions[c]}
                  </option>
                ))}
              </select>
            </Field>
          </div>
        )}

        {simActive && projData && (
          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <MiniStat
              label={es.investments.projectionFinal}
              value={formatCents(projData.finalValueCents, d.currencyCode)}
              accent
            />
            <MiniStat
              label={es.investments.projectionContributed}
              value={formatCents(projData.contributedCents, d.currencyCode)}
            />
            <MiniStat
              label={es.investments.projectionInterest}
              value={formatCents(projData.interestCents, d.currencyCode)}
              gold
            />
          </div>
        )}

        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={chartData}>
            <CartesianGrid stroke={chart.grid} strokeDasharray="3 3" />
            <XAxis dataKey="date" stroke={chart.axis} fontSize={11} minTickGap={40} />
            <YAxis
              stroke={chart.axis}
              fontSize={11}
              domain={["auto", "auto"]}
              tickFormatter={(v) => (Number(v) / 1000).toFixed(1) + "k"}
            />
            <Tooltip
              formatter={(v) =>
                v == null ? [] : formatCents(Math.round(Number(v) * 100), d.currencyCode)
              }
              contentStyle={chart.tooltip}
            />
            {chartData.some((p) => p.date >= todayStr) && (
              <ReferenceLine x={todayStr} stroke={chart.axis} strokeDasharray="4 4" />
            )}
            <Line
              type="monotone"
              dataKey="actual"
              name={es.investments.currentValue}
              stroke={POSITIVE}
              strokeWidth={2}
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
            <Line
              type="monotone"
              dataKey="projected"
              name={es.investments.projection}
              stroke={POSITIVE}
              strokeWidth={2}
              strokeDasharray="5 4"
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
            <Line
              type="monotone"
              dataKey="withContrib"
              name={es.investments.projectionWithContrib}
              stroke={SIM_GOLD}
              strokeWidth={2}
              strokeDasharray="5 4"
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </section>
      )}

      {d.calculator !== "manual" && (
        <section className="mb-4 rounded-xl border border-border-muted bg-surface-raised p-5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-medium">{es.investments.movements}</h3>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => openMovement("deposit")}>
                <span className="flex items-center gap-2 text-accent">
                  <ArrowDownLeft size={15} /> {es.investments.deposit}
                </span>
              </Button>
              <Button variant="ghost" onClick={() => openMovement("withdrawal")}>
                <span className="flex items-center gap-2 text-danger">
                  <ArrowUpRight size={15} /> {es.investments.withdrawal}
                </span>
              </Button>
            </div>
          </div>
          {d.movements.length === 0 ? (
            <p className="py-2 text-sm text-fg-subtle">{es.investments.movementsEmpty}</p>
          ) : (
            <ul className="divide-y divide-border-muted">
              {d.movements.map((m) => (
                <li key={m.id} className="group flex items-center gap-3 py-2 text-sm">
                  <span
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-surface-overlay ${
                      m.kind === "deposit" ? "text-accent" : "text-danger"
                    }`}
                  >
                    {m.kind === "deposit" ? (
                      <ArrowDownLeft size={13} />
                    ) : (
                      <ArrowUpRight size={13} />
                    )}
                  </span>
                  <span className="text-fg">
                    {m.kind === "deposit"
                      ? es.investments.depositNoun
                      : es.investments.withdrawalNoun}
                  </span>
                  <span className="text-fg-subtle">{m.occurredAt}</span>
                  <span
                    className={`ml-auto tabular-nums ${
                      m.kind === "deposit" ? "text-accent" : "text-danger"
                    }`}
                  >
                    {m.kind === "deposit" ? "+" : "−"}
                    {formatCents(m.amountCents, d.currencyCode)}
                  </span>
                  <button
                    onClick={() => setMovementToDelete(m.id)}
                    aria-label={es.common.delete}
                    className="touch-action-reveal rounded-md p-1 text-fg-subtle transition-all hover:bg-danger/10 hover:text-danger"
                  >
                    <Trash2 size={14} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {d.calculator === "manual" && (
        <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-medium">{es.investments.snapshots}</h3>
            <Button variant="ghost" onClick={() => setSnapshotOpen(true)}>
              <span className="flex items-center gap-2">
                <Plus size={15} /> {es.investments.addSnapshot}
              </span>
            </Button>
          </div>
          <ul className="divide-y divide-border-muted">
            {d.snapshots.map((s) => (
              <li key={s.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-fg-muted">{s.asOf}</span>
                <span className="tabular-nums">
                  {formatCents(s.valueCents, d.currencyCode)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <InvestmentFormModal open={editOpen} onClose={() => setEditOpen(false)} investment={d} />

      <Modal
        title={
          movementKind === "withdrawal"
            ? es.investments.withdrawalNoun
            : es.investments.depositNoun
        }
        open={movementKind !== null}
        onClose={() => setMovementKind(null)}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setMovementError(null);
            movement.mutate();
          }}
          className="grid gap-4"
        >
          <Field label={es.investments.movementAmount}>
            <MoneyInput value={movementValue} onChange={setMovementValue} required autoFocus />
          </Field>
          <Field label={es.investments.movementDate}>
            <DateInput value={movementDate} onChange={setMovementDate} min={d.startDate} />
          </Field>
          <Field label={es.investments.movementWallet}>
            <select
              className={inputClass}
              value={movementWalletId ?? ""}
              onChange={(e) =>
                setMovementWalletId(e.target.value === "" ? null : Number(e.target.value))
              }
            >
              <option value="">{es.investments.movementWalletNone}</option>
              {wallets.data?.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name} ({w.currencyCode})
                </option>
              ))}
            </select>
            {movementWalletId !== null && (
              <span className="mt-1 block text-xs text-fg-subtle">
                {movementKind === "withdrawal"
                  ? es.investments.movementWalletWithdrawalHint
                  : es.investments.movementWalletDepositHint}
              </span>
            )}
          </Field>
          {movementError && <p className="text-sm text-danger">{movementError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setMovementKind(null)}>
              {es.common.cancel}
            </Button>
            <Button type="submit" disabled={movement.isPending}>
              {es.common.save}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal
        title={es.investments.addSnapshot}
        open={snapshotOpen}
        onClose={() => setSnapshotOpen(false)}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setSnapshotError(null);
            snapshot.mutate();
          }}
          className="grid gap-4"
        >
          <Field label={es.investments.snapshotValue}>
            <MoneyInput value={snapshotValue} onChange={setSnapshotValue} required autoFocus />
          </Field>
          <Field label={es.investments.snapshotDate}>
            <DateInput value={snapshotDate} onChange={setSnapshotDate} />
          </Field>
          {snapshotError && <p className="text-sm text-danger">{snapshotError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setSnapshotOpen(false)}>
              {es.common.cancel}
            </Button>
            <Button type="submit" disabled={snapshot.isPending}>
              {es.common.save}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={confirmDeleteOpen}
        title={es.investments.deleteConfirmTitle}
        message={es.investments.deleteConfirm}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirmDeleteOpen(false)}
      />
      <ConfirmDialog
        open={movementToDelete !== null}
        title={es.investments.movementDeleteTitle}
        message={es.investments.movementDeleteConfirm}
        onConfirm={() => {
          if (movementToDelete !== null) removeMovement.mutate(movementToDelete);
        }}
        onClose={() => setMovementToDelete(null)}
      />
    </>
  );
}

function MiniStat({
  label,
  value,
  accent,
  gold,
}: {
  label: string;
  value: string;
  accent?: boolean;
  gold?: boolean;
}) {
  return (
    <div className="rounded-xl border border-border-muted bg-surface p-3">
      <p className="text-xs text-fg-subtle">{label}</p>
      <p
        className={`mt-0.5 font-display text-lg font-semibold tabular-nums ${
          accent ? "text-accent" : gold ? "text-gold" : "text-fg"
        }`}
      >
        {value}
      </p>
    </div>
  );
}
