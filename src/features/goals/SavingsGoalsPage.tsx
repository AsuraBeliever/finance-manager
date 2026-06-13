import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Pencil, PiggyBank, Plus, Trash2 } from "lucide-react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass } from "../../components/Field";
import { Gauge } from "../../components/Gauge";
import { Modal } from "../../components/Modal";
import { PageHeader } from "../../components/PageHeader";
import {
  contributeSavingsGoal,
  createSavingsGoal,
  deleteSavingsGoal,
  listCurrencies,
  listSavingsGoals,
  updateSavingsGoal,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import type { SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";

export function SavingsGoalsPage() {
  const qc = useQueryClient();
  const goals = useQuery({ queryKey: ["savingsGoals"], queryFn: listSavingsGoals });
  const [formGoal, setFormGoal] = useState<SavingsGoal | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [contribFor, setContribFor] = useState<SavingsGoal | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["savingsGoals"] });
  };
  const remove = useMutation({ mutationFn: deleteSavingsGoal, onSuccess: invalidate });

  const list = goals.data ?? [];

  return (
    <>
      <PageHeader
        title={es.goals.title}
        actions={
          <Button
            onClick={() => {
              setFormGoal(null);
              setFormOpen(true);
            }}
          >
            <span className="flex items-center gap-2">
              <Plus size={16} /> {es.goals.newGoal}
            </span>
          </Button>
        }
      />

      {goals.isSuccess && list.length === 0 && (
        <EmptyState
          icon={PiggyBank}
          title={es.goals.emptyTitle}
          description={es.goals.emptyDescription}
        />
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {list.map((g) => (
          <section
            key={g.id}
            className="rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card"
          >
            <div className="mb-2 flex items-center justify-between">
              <h3 className="truncate font-display text-lg font-medium text-fg">{g.name}</h3>
              <div className="flex shrink-0 gap-1">
                <button
                  onClick={() => {
                    setFormGoal(g);
                    setFormOpen(true);
                  }}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
                >
                  <Pencil size={15} />
                </button>
                <button
                  onClick={() => setDeleteId(g.id)}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
            <Gauge
              value={g.progressBps / 10000}
              color={g.color ?? "var(--color-accent)"}
              label={formatCents(g.savedCents, g.currencyCode)}
              sublabel={`${es.goals.of} ${formatCents(g.targetCents, g.currencyCode)}`}
            />
            <div className="mt-3 flex items-center justify-between">
              <span className="text-sm font-medium text-accent">
                {g.progressBps >= 10000
                  ? es.goals.completed
                  : `${Math.round(g.progressBps / 100)}%`}
              </span>
              <Button variant="ghost" onClick={() => setContribFor(g)}>
                {es.goals.contribute}
              </Button>
            </div>
          </section>
        ))}
      </div>

      <GoalFormModal
        open={formOpen}
        goal={formGoal}
        onClose={() => setFormOpen(false)}
        onSaved={invalidate}
      />
      <ContributeModal
        goal={contribFor}
        onClose={() => setContribFor(null)}
        onSaved={invalidate}
      />
      <ConfirmDialog
        open={deleteId !== null}
        title={es.common.delete}
        message={es.goals.deleteConfirm}
        confirmLabel={es.common.delete}
        onConfirm={() => {
          if (deleteId !== null) remove.mutate(deleteId);
          setDeleteId(null);
        }}
        onClose={() => setDeleteId(null)}
      />
    </>
  );
}

function GoalFormModal({
  open,
  goal,
  onClose,
  onSaved,
}: {
  open: boolean;
  goal: SavingsGoal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const [name, setName] = useState("");
  const [target, setTarget] = useState("");
  const [currency, setCurrency] = useState("MXN");
  const [color, setColor] = useState<string>(CHART_COLORS[0]);
  const [error, setError] = useState<string | null>(null);

  // Reset fields whenever the modal opens for a new/edited goal.
  const [lastKey, setLastKey] = useState<string>("");
  const key = `${open}-${goal?.id ?? "new"}`;
  if (open && key !== lastKey) {
    setLastKey(key);
    setName(goal?.name ?? "");
    setTarget(goal ? (goal.targetCents / 100).toString() : "");
    setCurrency(goal?.currencyCode ?? "MXN");
    setColor(goal?.color ?? CHART_COLORS[0]);
    setError(null);
  }

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(target);
      if (!name.trim() || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      const input = {
        name: name.trim(),
        icon: null,
        color,
        currencyCode: currency,
        targetCents: cents,
      };
      return goal ? updateSavingsGoal(goal.id, input) : createSavingsGoal(input);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal open={open} onClose={onClose} title={goal ? es.goals.editGoal : es.goals.newGoal}>
      <div className="flex flex-col gap-4">
        <Field label={es.goals.name}>
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.goals.target}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
            />
          </Field>
          <Field label={es.investments.currency}>
            <select
              className={inputClass}
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
            >
              {(currencies.data ?? []).map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label={es.common.color ?? "Color"}>
          <div className="flex flex-wrap gap-2">
            {CHART_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setColor(c)}
                className={`h-7 w-7 rounded-full transition-transform ${
                  color === c ? "scale-110 ring-2 ring-fg" : ""
                }`}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>
        </Field>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {es.common.save}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function ContributeModal({
  goal,
  onClose,
  onSaved,
}: {
  goal: SavingsGoal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(amount);
      if (!goal || cents === null || cents === 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      return contributeSavingsGoal(goal.id, cents);
    },
    onSuccess: () => {
      setAmount("");
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal open={goal !== null} onClose={onClose} title={es.goals.contributeTitle}>
      <div className="flex flex-col gap-4">
        <Field label={es.goals.amount}>
          <input
            className={inputClass}
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            autoFocus
          />
        </Field>
        <p className="-mt-2 text-xs text-fg-subtle">{es.goals.withdrawHint}</p>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {es.goals.contribute}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
