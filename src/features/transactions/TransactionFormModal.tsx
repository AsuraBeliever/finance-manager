import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { listTransactionCategories, listWallets, updateTransaction } from "../../lib/api";
import { submitOrQueue } from "../../lib/outbox";
import { parseToCents } from "../../lib/money";
import { todayIso } from "../../lib/date";
import type { Transaction } from "../../lib/types";
import { es } from "../../i18n/es";

type Tab = "income" | "expense" | "transfer";

interface TransactionFormModalProps {
  open: boolean;
  onClose: () => void;
  /** Preselected wallet (e.g. when opened from a wallet detail page). */
  defaultWalletId?: number;
  /** When set, the modal edits this transaction instead of creating one.
   *  Only income/expense are editable (transfers are delete + recreate). */
  transaction?: Transaction;
}

export function TransactionFormModal({
  open,
  onClose,
  defaultWalletId,
  transaction,
}: TransactionFormModalProps) {
  const isEdit = transaction !== undefined;
  const tabs: { id: Tab; label: string }[] = [
    { id: "income", label: es.transactions.income },
    { id: "expense", label: es.transactions.expense },
    { id: "transfer", label: es.transactions.transfer },
  ];
  const queryClient = useQueryClient();
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const categories = useQuery({
    queryKey: ["transactionCategories"],
    queryFn: listTransactionCategories,
  });

  const [tab, setTab] = useState<Tab>("income");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [toWalletId, setToWalletId] = useState<number | null>(null);
  const [amountText, setAmountText] = useState("");
  const [amountToText, setAmountToText] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setToWalletId(null);
    setAmountToText("");
    if (transaction) {
      // Edit mode: prefill from the existing income/expense.
      setTab(transaction.kind === "expense" ? "expense" : "income");
      setWalletId(transaction.walletId);
      setAmountText((transaction.amountCents / 100).toFixed(2));
      setCategoryId(transaction.categoryId);
      setDescription(transaction.description ?? "");
      setDate(transaction.occurredAt);
    } else {
      setTab("income");
      setWalletId(defaultWalletId ?? null);
      setAmountText("");
      setCategoryId(null);
      setDescription("");
      setDate(todayIso());
    }
  }, [open, defaultWalletId, transaction]);

  const walletList = wallets.data ?? [];
  const fromWallet = walletList.find((w) => w.id === (walletId ?? walletList[0]?.id));
  const toWallet = walletList.find((w) => w.id === (toWalletId ?? undefined));
  const crossCurrency =
    tab === "transfer" &&
    fromWallet &&
    toWallet &&
    fromWallet.currencyCode !== toWallet.currencyCode;

  const visibleCategories = (categories.data ?? []).filter((c) => c.kind === tab);

  const mutation = useMutation({
    mutationFn: async () => {
      const cents = parseToCents(amountText);
      if (cents === null || cents <= 0) throw new Error(es.transactions.invalidAmount);
      const wid = walletId ?? walletList[0]?.id;
      if (wid === undefined) throw new Error(es.transactions.wallet);
      const common = {
        walletId: wid,
        amountCents: cents,
        categoryId,
        description: description.trim() === "" ? null : description.trim(),
        occurredAt: date,
      };
      // Edits go straight to the server (online-only, like delete); no outbox.
      if (transaction) return updateTransaction(transaction.id, common);
      // Captures go through the offline outbox: sent right away when online,
      // queued (and replayed idempotently) when there is no connection.
      if (tab === "income") return submitOrQueue("add_income", common);
      if (tab === "expense") return submitOrQueue("add_expense", common);

      const toId = toWalletId ?? walletList.find((w) => w.id !== wid)?.id;
      if (toId === undefined) throw new Error(es.transactions.toWallet);
      const toCents = crossCurrency ? parseToCents(amountToText) : cents;
      if (toCents === null || toCents <= 0)
        throw new Error(es.transactions.invalidAmount);
      return submitOrQueue("add_transfer", {
        fromWalletId: wid,
        toWalletId: toId,
        amountFromCents: cents,
        amountToCents: toCents,
        description: common.description,
        occurredAt: date,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal
      title={isEdit ? es.transactions.editTransaction : es.transactions.newTransaction}
      open={open}
      onClose={onClose}
    >
      {!isEdit && (
        <div className="mb-4 flex rounded-lg bg-surface p-1">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                setTab(t.id);
                setCategoryId(null);
                setError(null);
              }}
              className={`flex-1 rounded-md px-3 py-1.5 text-sm transition-colors ${
                tab === t.id
                  ? "bg-surface-overlay font-medium text-fg"
                  : "text-fg-subtle hover:text-fg"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          mutation.mutate();
        }}
        className="grid gap-4"
      >
        <Field label={tab === "transfer" ? es.transactions.fromWallet : es.transactions.wallet}>
          <select
            className={inputClass}
            value={walletId ?? walletList[0]?.id ?? ""}
            onChange={(e) => setWalletId(Number(e.target.value))}
          >
            {walletList.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} ({w.currencyCode})
              </option>
            ))}
          </select>
        </Field>

        {tab === "transfer" && (
          <Field label={es.transactions.toWallet}>
            <select
              className={inputClass}
              value={toWalletId ?? walletList.find((w) => w.id !== (walletId ?? walletList[0]?.id))?.id ?? ""}
              onChange={(e) => setToWalletId(Number(e.target.value))}
            >
              {walletList
                .filter((w) => w.id !== (walletId ?? walletList[0]?.id))
                .map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name} ({w.currencyCode})
                  </option>
                ))}
            </select>
          </Field>
        )}

        <div className="grid grid-cols-2 gap-3">
          <Field label={es.transactions.amount}>
            <input
              className={inputClass}
              value={amountText}
              onChange={(e) => setAmountText(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              required
              autoFocus
            />
          </Field>
          <Field label={es.transactions.date}>
            <DateInput value={date} onChange={setDate} />
          </Field>
        </div>

        {crossCurrency && (
          <Field label={`${es.transactions.amountReceived} (${toWallet?.currencyCode})`}>
            <input
              className={inputClass}
              value={amountToText}
              onChange={(e) => setAmountToText(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              required
            />
            <span className="mt-1 block text-xs text-fg-subtle">
              {es.transactions.transferHint}
            </span>
          </Field>
        )}

        {tab !== "transfer" && (
          <Field label={es.transactions.category}>
            <select
              className={inputClass}
              value={categoryId ?? ""}
              onChange={(e) =>
                setCategoryId(e.target.value === "" ? null : Number(e.target.value))
              }
            >
              <option value="">{es.transactions.noCategory}</option>
              {visibleCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
        )}

        <Field label={es.transactions.description}>
          <input
            className={inputClass}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button type="submit" disabled={mutation.isPending || walletList.length === 0}>
            {es.common.save}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
