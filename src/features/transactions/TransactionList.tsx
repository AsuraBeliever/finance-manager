import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  PiggyBank,
  Pencil,
  Trash2,
} from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { Field } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { MoneyInput } from "../../components/MoneyInput";
import {
  deleteGoalContribution,
  deleteTransaction,
  updateGoalContribution,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { formatTime } from "../../lib/date";
import type { Transaction } from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";

const kindMeta: Record<
  Transaction["kind"],
  { icon: typeof ArrowUpRight; sign: string; color: string }
> = {
  income: { icon: ArrowDownLeft, sign: "+", color: "text-accent" },
  expense: { icon: ArrowUpRight, sign: "−", color: "text-danger" },
  transfer_in: { icon: ArrowLeftRight, sign: "+", color: "text-sky-400" },
  transfer_out: { icon: ArrowLeftRight, sign: "−", color: "text-sky-400" },
  // Apartado moves (info only): neutral colour, no +/−, since no money leaves
  // the wallet — they're earmarks shown for tracking.
  reserve: { icon: PiggyBank, sign: "→", color: "text-fg-muted" },
  release: { icon: PiggyBank, sign: "←", color: "text-fg-muted" },
};

const apartadoKinds = new Set<Transaction["kind"]>(["reserve", "release"]);

interface TransactionListProps {
  transactions: Transaction[];
  /** Currency for amounts; falls back to MXN per row if not provided. */
  currencyByWallet?: Map<number, string>;
  showWallet?: boolean;
  /** When provided, income/expense rows get an edit button (transfers don't). */
  onEdit?: (t: Transaction) => void;
}

export function TransactionList({
  transactions,
  currencyByWallet,
  showWallet = true,
  onEdit,
}: TransactionListProps) {
  const queryClient = useQueryClient();
  const [toDelete, setToDelete] = useState<number | null>(null);
  // Apartado rows are goal_contributions surfaced with a negated id; editing
  // or deleting them adjusts the goal's earmark, so they get their own flow.
  const [apartadoToDelete, setApartadoToDelete] = useState<Transaction | null>(null);
  const [apartadoToEdit, setApartadoToEdit] = useState<Transaction | null>(null);
  const [editAmount, setEditAmount] = useState("");
  const [editDate, setEditDate] = useState("");
  const [editError, setEditError] = useState<string | null>(null);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
    queryClient.invalidateQueries({ queryKey: ["wallets"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };
  const invalidateWithGoals = () => {
    invalidate();
    queryClient.invalidateQueries({ queryKey: ["savingsGoals"] });
  };

  const remove = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: invalidate,
  });

  const removeApartado = useMutation({
    mutationFn: (t: Transaction) => deleteGoalContribution(-t.id),
    onSuccess: invalidateWithGoals,
  });

  const editApartado = useMutation({
    mutationFn: (t: Transaction) => {
      const cents = parseToCents(editAmount);
      if (cents === null || cents <= 0)
        return Promise.reject(new Error(es.transactions.invalidAmount));
      const signed = t.kind === "release" ? -cents : cents;
      return updateGoalContribution(-t.id, signed, editDate);
    },
    onSuccess: () => {
      invalidateWithGoals();
      setApartadoToEdit(null);
    },
    onError: (e) => setEditError(e instanceof Error ? e.message : String(e)),
  });

  const openApartadoEdit = (t: Transaction) => {
    setEditAmount((Math.abs(t.amountCents) / 100).toFixed(2));
    setEditDate(t.occurredAt);
    setEditError(null);
    setApartadoToEdit(t);
  };

  return (
    <>
    <ul className="divide-y divide-border-muted rounded-xl border border-border-muted bg-surface-raised">
      {transactions.map((t) => {
        const meta = kindMeta[t.kind];
        const Icon = meta.icon;
        const currency = currencyByWallet?.get(t.walletId) ?? "MXN";
        const isApartado = apartadoKinds.has(t.kind);
        return (
          <li key={t.id} className="group flex items-center gap-3 px-4 py-3">
            <span
              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-overlay ${meta.color}`}
            >
              <Icon size={15} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm">
                {isApartado
                  ? `${es.transactions[t.kind === "reserve" ? "reserved" : "released"]} · ${t.description ?? ""}`
                  : t.description ||
                    (t.categoryName && seedName(t.categoryName)) ||
                    es.transactions[
                      t.kind === "income"
                        ? "income"
                        : t.kind === "expense"
                          ? "expense"
                          : "transfer"
                    ]}
              </p>
              <p className="text-xs text-fg-subtle">
                {t.occurredAt}
                {t.occurredTime && <> · {formatTime(t.occurredTime)}</>}
                {showWallet && <> · {t.walletName}</>}
                {t.categoryName && t.description && <> · {seedName(t.categoryName)}</>}
              </p>
            </div>
            <span className={`text-sm font-medium tabular-nums ${meta.color}`}>
              {meta.sign}
              {formatCents(t.amountCents, currency)}
            </span>
            {/* Fixed-width action slot so amounts line up whether a row has 0, 1
                or 2 buttons (transfers only have delete). */}
            <div className="flex w-16 shrink-0 items-center justify-end gap-1">
              {(isApartado || (onEdit && (t.kind === "income" || t.kind === "expense"))) && (
                <button
                  onClick={() => (isApartado ? openApartadoEdit(t) : onEdit?.(t))}
                  aria-label={es.common.edit}
                  className="touch-action-reveal rounded-md p-1.5 text-fg-subtle transition-all hover:bg-surface-overlay hover:text-fg"
                >
                  <Pencil size={15} />
                </button>
              )}
              <button
                onClick={() => (isApartado ? setApartadoToDelete(t) : setToDelete(t.id))}
                aria-label={es.common.delete}
                className="touch-action-reveal rounded-md p-1.5 text-fg-subtle transition-all hover:bg-danger/10 hover:text-danger"
              >
                <Trash2 size={15} />
              </button>
            </div>
          </li>
        );
      })}
    </ul>
    <ConfirmDialog
      open={toDelete !== null}
      title={es.transactions.deleteConfirmTitle}
      message={es.transactions.deleteConfirm}
      onConfirm={() => {
        if (toDelete !== null) remove.mutate(toDelete);
      }}
      onClose={() => setToDelete(null)}
    />
    <ConfirmDialog
      open={apartadoToDelete !== null}
      title={es.transactions.apartadoDeleteTitle}
      message={es.transactions.apartadoDeleteConfirm}
      onConfirm={() => {
        if (apartadoToDelete !== null) removeApartado.mutate(apartadoToDelete);
      }}
      onClose={() => setApartadoToDelete(null)}
    />
    <Modal
      title={es.transactions.apartadoEditTitle}
      open={apartadoToEdit !== null}
      onClose={() => setApartadoToEdit(null)}
    >
      {apartadoToEdit && (
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            editApartado.mutate(apartadoToEdit);
          }}
        >
          <p className="text-sm text-fg-muted">
            {es.transactions[apartadoToEdit.kind === "reserve" ? "reserved" : "released"]}
            {" · "}
            {apartadoToEdit.description}
          </p>
          <Field label={es.transactions.amount}>
            <MoneyInput value={editAmount} onChange={setEditAmount} autoFocus />
          </Field>
          <Field label={es.transactions.date}>
            <DateInput value={editDate} onChange={setEditDate} />
          </Field>
          {editError && <p className="text-sm text-danger">{editError}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setApartadoToEdit(null)}>
              {es.common.cancel}
            </Button>
            <Button type="submit" variant="primary" disabled={editApartado.isPending}>
              {es.common.save}
            </Button>
          </div>
        </form>
      )}
    </Modal>
    </>
  );
}
