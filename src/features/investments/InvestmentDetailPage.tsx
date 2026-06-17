import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Lock,
  LockOpen,
  Pencil,
  Plus,
  Trash2,
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
import { Modal } from "../../components/Modal";
import {
  addInvestmentMovement,
  addSnapshot,
  closeInvestment,
  deleteInvestment,
  deleteInvestmentMovement,
  getExchangeRates,
  getInvestmentDetail,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { todayIso } from "../../lib/date";
import { POSITIVE, useChartTokens } from "../../lib/palette";
import { es } from "../../i18n/es";
import { InvestmentFormModal } from "./InvestmentFormModal";

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
  const [movementError, setMovementError] = useState<string | null>(null);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [movementToDelete, setMovementToDelete] = useState<number | null>(null);

  const detail = useQuery({
    queryKey: ["investments", invId],
    queryFn: () => getInvestmentDetail(invId),
    enabled: Number.isFinite(invId),
  });

  const fxRates = useQuery({ queryKey: ["exchangeRates"], queryFn: getExchangeRates });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["investments"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
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
      return addInvestmentMovement(invId, movementKind, cents, movementDate);
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

  if (detail.isPending) return <p className="text-sm text-fg-subtle">{es.common.loading}</p>;
  if (detail.isError) return <p className="text-sm text-danger">{String(detail.error)}</p>;

  const d = detail.data;
  const gainPositive = d.gainCents >= 0;
  const chartData = d.projection.map((p) => ({
    date: p.date,
    value: p.valueCents / 100,
  }));
  const todayStr = todayIso();

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
        <h3 className="mb-4 font-display text-lg font-medium tracking-tight text-fg">{es.investments.projection}</h3>
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
              formatter={(v) => formatCents(Math.round(Number(v) * 100), d.currencyCode)}
              contentStyle={chart.tooltip}
            />
            {chartData.some((p) => p.date >= todayStr) && (
              <ReferenceLine x={todayStr} stroke={chart.axis} strokeDasharray="4 4" />
            )}
            <Line
              type="monotone"
              dataKey="value"
              stroke={POSITIVE}
              strokeWidth={2}
              dot={false}
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
              <Button variant="ghost" onClick={() => setMovementKind("deposit")}>
                <span className="flex items-center gap-2 text-accent">
                  <ArrowDownLeft size={15} /> {es.investments.deposit}
                </span>
              </Button>
              <Button variant="ghost" onClick={() => setMovementKind("withdrawal")}>
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
                    className="rounded-md p-1 text-fg-subtle opacity-0 transition-all hover:bg-danger/10 hover:text-danger group-hover:opacity-100"
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
            <input
              className={inputClass}
              value={movementValue}
              onChange={(e) => setMovementValue(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              required
              autoFocus
            />
          </Field>
          <Field label={es.investments.movementDate}>
            <DateInput value={movementDate} onChange={setMovementDate} min={d.startDate} />
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
            <input
              className={inputClass}
              value={snapshotValue}
              onChange={(e) => setSnapshotValue(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              required
              autoFocus
            />
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
