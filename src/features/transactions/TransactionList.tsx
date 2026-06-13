import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, Trash2 } from "lucide-react";
import { useState } from "react";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { deleteTransaction } from "../../lib/api";
import { formatCents } from "../../lib/money";
import type { Transaction } from "../../lib/types";
import { es } from "../../i18n/es";

const kindMeta: Record<
  Transaction["kind"],
  { icon: typeof ArrowUpRight; sign: string; color: string }
> = {
  income: { icon: ArrowDownLeft, sign: "+", color: "text-accent" },
  expense: { icon: ArrowUpRight, sign: "−", color: "text-danger" },
  transfer_in: { icon: ArrowLeftRight, sign: "+", color: "text-sky-400" },
  transfer_out: { icon: ArrowLeftRight, sign: "−", color: "text-sky-400" },
};

interface TransactionListProps {
  transactions: Transaction[];
  /** Currency for amounts; falls back to MXN per row if not provided. */
  currencyByWallet?: Map<number, string>;
  showWallet?: boolean;
}

export function TransactionList({
  transactions,
  currencyByWallet,
  showWallet = true,
}: TransactionListProps) {
  const queryClient = useQueryClient();
  const [toDelete, setToDelete] = useState<number | null>(null);
  const remove = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  return (
    <>
    <ul className="divide-y divide-border-muted rounded-xl border border-border-muted bg-surface-raised">
      {transactions.map((t) => {
        const meta = kindMeta[t.kind];
        const Icon = meta.icon;
        const currency = currencyByWallet?.get(t.walletId) ?? "MXN";
        return (
          <li key={t.id} className="group flex items-center gap-3 px-4 py-3">
            <span
              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-overlay ${meta.color}`}
            >
              <Icon size={15} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm">
                {t.description ||
                  t.categoryName ||
                  es.transactions[
                    t.kind === "income"
                      ? "income"
                      : t.kind === "expense"
                        ? "expense"
                        : "transfer"
                  ]}
              </p>
              <p className="text-xs text-stone-500">
                {t.occurredAt}
                {showWallet && <> · {t.walletName}</>}
                {t.categoryName && t.description && <> · {t.categoryName}</>}
              </p>
            </div>
            <span className={`text-sm font-medium tabular-nums ${meta.color}`}>
              {meta.sign}
              {formatCents(t.amountCents, currency)}
            </span>
            <button
              onClick={() => setToDelete(t.id)}
              aria-label={es.common.delete}
              className="rounded-md p-1.5 text-stone-600 opacity-0 transition-all hover:bg-danger/10 hover:text-danger group-hover:opacity-100"
            >
              <Trash2 size={15} />
            </button>
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
    </>
  );
}
