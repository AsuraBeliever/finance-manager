import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "../../components/Button";
import { Field } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import { contributeSavingsGoal, listWallets } from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import type { SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";

export function ContributeModal({
  goal,
  onClose,
  onSaved,
}: {
  goal: SavingsGoal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  // Two clear actions, both with positive amounts: "reserve" adds money to the
  // goal, "release" returns it. The backend still takes a signed delta — we
  // negate behind the scenes so the user never types a negative number.
  const [mode, setMode] = useState<"reserve" | "release">("reserve");
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  // Reset on every open (closing clears lastId), so reopening the same goal
  // never lands on the previous visit's tab or leftover amount.
  const [lastId, setLastId] = useState<number | null>(null);
  if (!goal && lastId !== null) {
    setLastId(null);
  }
  if (goal && goal.id !== lastId) {
    setLastId(goal.id);
    setMode("reserve");
    setAmount("");
    setError(null);
  }

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(amount);
      if (!goal || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      if (mode === "release" && cents > goal.savedCents)
        return Promise.reject(
          new Error(
            es.goals.releaseTooMuch.replace(
              "{amount}",
              formatCents(goal.savedCents, goal.currencyCode),
            ),
          ),
        );
      return contributeSavingsGoal(goal.id, mode === "release" ? -cents : cents);
    },
    onSuccess: () => {
      setAmount("");
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  // For an apartado goal, show how much is free to reserve in its wallet.
  const apartadoWallet = goal?.linkedWalletId
    ? (wallets.data?.find((w) => w.id === goal.linkedWalletId) ?? null)
    : null;
  const available = apartadoWallet
    ? apartadoWallet.balanceCents - apartadoWallet.reservedCents
    : null;
  const currency = goal?.currencyCode ?? "MXN";
  const canRelease = (goal?.savedCents ?? 0) > 0;
  // Suggest what's missing to cover this period; once covered, the next
  // period's split.
  const suggested =
    mode === "reserve"
      ? goal?.plan?.periodMissingCents || (goal?.plan?.perPeriodCents ?? 0)
      : 0;

  const tab = (m: "reserve" | "release", label: string) => (
    <button
      type="button"
      onClick={() => setMode(m)}
      className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
        mode === m
          ? "bg-surface-raised text-fg shadow-sm"
          : "text-fg-subtle hover:text-fg"
      }`}
    >
      {label}
    </button>
  );

  return (
    <Modal open={goal !== null} onClose={onClose} title={es.goals.contributeTitle}>
      <div className="flex flex-col gap-4">
        <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
          {tab("reserve", es.goals.reserveTab)}
          {canRelease && tab("release", es.goals.releaseTab)}
        </div>

        {mode === "reserve" && apartadoWallet && available !== null && (
          <p className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
            {es.goals.apartadoOf} <span className="font-medium text-fg">{apartadoWallet.name}</span> ·{" "}
            {es.goals.available}{" "}
            <span className="font-medium tabular-nums text-fg">
              {formatCents(available, apartadoWallet.currencyCode)}
            </span>
          </p>
        )}
        {mode === "release" && goal && (
          <p className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
            {es.goals.reservedLabel}{" "}
            <span className="font-medium tabular-nums text-fg">
              {formatCents(goal.savedCents, currency)}
            </span>
          </p>
        )}

        <Field label={es.goals.amount}>
          <MoneyInput value={amount} onChange={setAmount} autoFocus />
        </Field>
        {suggested > 0 && (
          <button
            type="button"
            onClick={() => setAmount((suggested / 100).toString())}
            className="-mt-2 self-start rounded-lg bg-accent/10 px-3 py-1.5 text-xs font-medium text-accent transition-colors hover:bg-accent/20"
          >
            {es.goals.suggestedChip} {formatCents(suggested, currency)}
          </button>
        )}
        <p className="-mt-1 text-xs text-fg-subtle">
          {mode === "reserve"
            ? apartadoWallet
              ? es.goals.reserveHint
              : es.goals.reserveTrackHint
            : apartadoWallet
              ? es.goals.releaseHint
              : es.goals.releaseTrackHint}
        </p>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {mode === "reserve" ? es.goals.reserveAction : es.goals.releaseAction}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
