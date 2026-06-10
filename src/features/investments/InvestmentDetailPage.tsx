import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Lock, LockOpen, Pencil, Plus, Trash2 } from "lucide-react";
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
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import {
  addSnapshot,
  closeInvestment,
  deleteInvestment,
  getInvestmentDetail,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { es } from "../../i18n/es";
import { InvestmentFormModal } from "./InvestmentFormModal";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function InvestmentDetailPage() {
  const { id } = useParams();
  const invId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);
  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotValue, setSnapshotValue] = useState("");
  const [snapshotDate, setSnapshotDate] = useState(today());
  const [snapshotError, setSnapshotError] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ["investments", invId],
    queryFn: () => getInvestmentDetail(invId),
    enabled: Number.isFinite(invId),
  });

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
      setSnapshotDate(today());
    },
    onError: (e) => setSnapshotError(e instanceof Error ? e.message : String(e)),
  });

  if (detail.isPending) return <p className="text-sm text-zinc-500">{es.common.loading}</p>;
  if (detail.isError) return <p className="text-sm text-danger">{String(detail.error)}</p>;

  const d = detail.data;
  const gainPositive = d.gainCents >= 0;
  const chartData = d.projection.map((p) => ({
    date: p.date,
    value: p.valueCents / 100,
  }));
  const todayStr = today();

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
          <Button
            variant="danger"
            onClick={() => {
              if (window.confirm(es.investments.deleteConfirm)) remove.mutate();
            }}
          >
            <span className="flex items-center gap-2">
              <Trash2 size={15} /> {es.common.delete}
            </span>
          </Button>
        </div>
      </div>

      <div className="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-zinc-500">{es.investments.currentValue}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">
            {formatCents(d.currentValueCents, d.currencyCode)}
          </p>
        </div>
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-zinc-500">{es.investments.gain}</p>
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
          <p className="text-xs text-zinc-500">{es.investments.principal}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums">
            {formatCents(d.principalCents, d.currencyCode)}
          </p>
        </div>
        <div className="rounded-xl border border-border-muted bg-surface-raised p-4">
          <p className="text-xs text-zinc-500">
            {d.maturityDate ? es.investments.maturity : es.investments.startDate}
          </p>
          <p className="mt-1 text-xl font-semibold">{d.maturityDate ?? d.startDate}</p>
        </div>
      </div>

      <section className="mb-4 rounded-xl border border-border-muted bg-surface-raised p-5">
        <h3 className="mb-2 font-medium">{es.investments.projection}</h3>
        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={chartData}>
            <CartesianGrid stroke="#2a2f3d" strokeDasharray="3 3" />
            <XAxis dataKey="date" stroke="#71717a" fontSize={11} minTickGap={40} />
            <YAxis
              stroke="#71717a"
              fontSize={11}
              domain={["auto", "auto"]}
              tickFormatter={(v) => (Number(v) / 1000).toFixed(1) + "k"}
            />
            <Tooltip
              formatter={(v) => formatCents(Math.round(Number(v) * 100), d.currencyCode)}
              contentStyle={{
                backgroundColor: "#1f2330",
                border: "1px solid #2a2f3d",
                borderRadius: 8,
              }}
            />
            {chartData.some((p) => p.date >= todayStr) && (
              <ReferenceLine x={todayStr} stroke="#71717a" strokeDasharray="4 4" />
            )}
            <Line
              type="monotone"
              dataKey="value"
              stroke="#34d399"
              strokeWidth={2}
              dot={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </section>

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
                <span className="text-zinc-400">{s.asOf}</span>
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
            <input
              type="date"
              className={inputClass}
              value={snapshotDate}
              onChange={(e) => setSnapshotDate(e.target.value)}
              required
            />
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
    </>
  );
}
