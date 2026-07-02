import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import {
  createMsiPlan,
  listTransactionCategories,
  listWallets,
  updateTransaction,
} from "../../lib/api";
import { submitOrQueue } from "../../lib/outbox";
import { parseToCents } from "../../lib/money";
import { todayIso } from "../../lib/date";
import type { Transaction } from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";

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
  const [msiEnabled, setMsiEnabled] = useState(false);
  const [msiMonthsText, setMsiMonthsText] = useState("12");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setToWalletId(null);
    setAmountToText("");
    setMsiEnabled(false);
    setMsiMonthsText("12");
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

  // New expenses on a configured credit card can be MSI purchases: instead of
  // one expense, an MSI plan is created and each installment posts itself on
  // its cut date (see CreditCardPanel — this is the same flow, reachable from
  // anywhere). Edits never convert to/from MSI.
  const isCreditWallet = !isEdit && tab === "expense" && fromWallet?.creditCutDay != null;
  const msiActive = isCreditWallet && msiEnabled;

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
      // MSI purchase: creates the plan (online-only); the installments post
      // themselves on each cut date, so no expense is booked here.
      if (msiActive) {
        if (common.description === null)
          throw new Error(es.credit.msiNeedsDescription);
        const months = parseInt(msiMonthsText, 10);
        if (!isFinite(months) || months < 2 || months > 60)
          throw new Error(es.credit.msiInvalidMonths);
        return createMsiPlan({
          walletId: wid,
          description: common.description,
          totalCents: cents,
          months,
          purchasedAt: date,
        });
      }
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
      queryClient.invalidateQueries({ queryKey: ["creditCard"] });
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
            <MoneyInput value={amountText} onChange={setAmountText} required autoFocus />
          </Field>
          <Field label={es.transactions.date}>
            <DateInput value={date} onChange={setDate} />
          </Field>
        </div>

        {crossCurrency && (
          <Field label={`${es.transactions.amountReceived} (${toWallet?.currencyCode})`}>
            <MoneyInput value={amountToText} onChange={setAmountToText} required />
            <span className="mt-1 block text-xs text-fg-subtle">
              {es.transactions.transferHint}
            </span>
          </Field>
        )}

        {/* MSI purchase on a credit card: replaces the plain expense with an
            installment plan. Category is hidden — the monthly charges post
            under the reserved MSI category. */}
        {isCreditWallet && (
          <div className="rounded-lg border border-border-muted p-3">
            <label className="flex cursor-pointer items-start gap-2.5">
              <input
                type="checkbox"
                checked={msiEnabled}
                onChange={(e) => setMsiEnabled(e.target.checked)}
                className="mt-0.5 h-4 w-4 accent-accent"
              />
              <span className="text-sm">
                {es.credit.msiToggle}
                <span className="mt-0.5 block text-xs text-fg-subtle">
                  {es.credit.msiToggleHint}
                </span>
              </span>
            </label>
            {msiEnabled && (
              <div className="mt-3 grid grid-cols-2 gap-3">
                <Field label={es.credit.msiMonths}>
                  <input
                    className={inputClass}
                    value={msiMonthsText}
                    onChange={(e) => setMsiMonthsText(e.target.value)}
                    inputMode="numeric"
                  />
                </Field>
              </div>
            )}
          </div>
        )}

        {tab !== "transfer" && !msiActive && (
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
                  {seedName(c.name, c.isSystem)}
                </option>
              ))}
            </select>
          </Field>
        )}

        <Field label={msiActive ? es.credit.msiDescription : es.transactions.description}>
          <input
            className={inputClass}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder={msiActive ? es.credit.msiDescriptionPlaceholder : undefined}
            required={msiActive}
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
