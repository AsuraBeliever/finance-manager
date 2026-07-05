import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ColorPicker } from "../../components/ColorPicker";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import { createSavingsGoal, listWallets, updateSavingsGoal } from "../../lib/api";
import { parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import type { GoalCadence, GoalKind, SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";

/** Today's date as ISO 'YYYY-MM-DD' (the earliest selectable deadline). */
function todayISO(): string {
  return new Date().toISOString().slice(0, 10);
}

/** A sensible default deadline (~3 months out) prefilled when the user turns
 *  on a deadline, so they can just tweak it instead of starting from scratch. */
function defaultDeadlineISO(): string {
  const d = new Date();
  d.setMonth(d.getMonth() + 3);
  return d.toISOString().slice(0, 10);
}

export function GoalFormModal({
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
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const [name, setName] = useState("");
  const [target, setTarget] = useState("");
  const [currency, setCurrency] = useState("MXN");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [goalKind, setGoalKind] = useState<GoalKind>("purchase");
  const [color, setColor] = useState<string>(CHART_COLORS[0]);
  const [deadlineEnabled, setDeadlineEnabled] = useState(false);
  const [deadline, setDeadline] = useState<string>("");
  const [cadence, setCadence] = useState<GoalCadence>("monthly");
  const [error, setError] = useState<string | null>(null);

  // Reset fields whenever the modal opens for a new/edited goal. New goals
  // default to the first wallet (every goal is now backed by real money).
  const [lastKey, setLastKey] = useState<string>("");
  const key = `${open}-${goal?.id ?? "new"}`;
  if (open && key !== lastKey) {
    setLastKey(key);
    setName(goal?.name ?? "");
    setTarget(goal ? (goal.targetCents / 100).toString() : "");
    setCurrency(goal?.currencyCode ?? "MXN");
    setWalletId(goal?.linkedWalletId ?? wallets.data?.[0]?.id ?? null);
    setGoalKind(goal?.goalKind ?? "purchase");
    setColor(goal?.color ?? CHART_COLORS[0]);
    setDeadlineEnabled(goal?.targetDate != null);
    setDeadline(goal?.targetDate ?? "");
    setCadence(goal?.cadence ?? "monthly");
    setError(null);
  }

  // Fall back to the first wallet if none is chosen yet (e.g. the wallets query
  // hadn't resolved when the modal opened), so a new goal always has one.
  const effectiveWalletId = walletId ?? wallets.data?.[0]?.id ?? null;
  const linkedWallet = wallets.data?.find((w) => w.id === effectiveWalletId) ?? null;
  const effectiveCurrency = linkedWallet?.currencyCode ?? currency;

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(target);
      if (!name.trim() || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      if (effectiveWalletId == null)
        return Promise.reject(new Error(es.goals.apartadoWallet));
      const input = {
        name: name.trim(),
        icon: null,
        color,
        currencyCode: effectiveCurrency,
        targetCents: cents,
        walletId: effectiveWalletId,
        targetDate: deadlineEnabled && deadline ? deadline : null,
        cadence: deadlineEnabled && deadline ? cadence : null,
        goalKind,
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
        <Field label={es.goals.kindLabel}>
          <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
            {(
              [
                ["purchase", es.goals.kindPurchase],
                ["fund", es.goals.kindFund],
              ] as const
            ).map(([val, label]) => (
              <button
                key={val}
                type="button"
                onClick={() => setGoalKind(val)}
                className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  goalKind === val
                    ? "bg-surface-raised text-fg shadow-sm"
                    : "text-fg-subtle hover:text-fg"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          <span className="mt-1 block text-xs text-fg-subtle">
            {goalKind === "fund" ? es.goals.kindFundHint : es.goals.kindPurchaseHint}
          </span>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.goals.target}>
            <MoneyInput value={target} onChange={setTarget} />
          </Field>
          <Field label={es.investments.currency}>
            <input className={inputClass} value={effectiveCurrency} disabled readOnly />
          </Field>
        </div>
        <Field label={es.goals.apartadoWallet}>
          <select
            className={inputClass}
            value={effectiveWalletId ?? ""}
            onChange={(e) => setWalletId(e.target.value === "" ? null : Number(e.target.value))}
          >
            {wallets.data?.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} ({w.currencyCode})
              </option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-fg-subtle">{es.goals.apartadoHint}</span>
        </Field>
        {/* Deadline is opt-in via a clear switch; flipping it on reveals the
            date + cadence right away (prefilled), so it's configured in place. */}
        <div className="rounded-lg border border-border-muted p-3">
          <label className="flex cursor-pointer items-start gap-2.5">
            <input
              type="checkbox"
              checked={deadlineEnabled}
              onChange={(e) => {
                const on = e.target.checked;
                setDeadlineEnabled(on);
                if (on && !deadline) setDeadline(defaultDeadlineISO());
              }}
              className="mt-0.5 h-4 w-4 accent-accent"
            />
            <span className="text-sm">
              {es.goals.enableDeadline}
              <span className="mt-0.5 block text-xs text-fg-subtle">
                {es.goals.deadlineHint}
              </span>
            </span>
          </label>

          {deadlineEnabled && (
            <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label={es.goals.deadlineDateLabel}>
                <DateInput value={deadline} onChange={setDeadline} min={todayISO()} />
              </Field>
              <Field label={es.goals.cadenceLabel}>
                <select
                  className={inputClass}
                  value={cadence}
                  onChange={(e) => setCadence(e.target.value as GoalCadence)}
                >
                  <option value="daily">{es.goals.cadenceDaily}</option>
                  <option value="weekly">{es.goals.cadenceWeekly}</option>
                  <option value="monthly">{es.goals.cadenceMonthly}</option>
                  <option value="yearly">{es.goals.cadenceYearly}</option>
                </select>
              </Field>
            </div>
          )}
        </div>
        <Field label={es.common.color ?? "Color"}>
          <ColorPicker value={color} onChange={setColor} />
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
