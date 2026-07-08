import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button } from "../../components/Button";
import { DateInput } from "../../components/DateInput";
import { TimeInput } from "../../components/TimeInput";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { Modal } from "../../components/Modal";
import {
  createMsiPlan,
  getCreditCardSummary,
  getTransfer,
  listTransactionCategories,
  listWallets,
  updateTransaction,
  updateTransfer,
} from "../../lib/api";
import { submitOrQueue } from "../../lib/outbox";
import { formatCents, parseToCents } from "../../lib/money";
import { formatDayMonth, nowTime, timeInputValue, todayIso } from "../../lib/date";
import { getTimezone } from "../../lib/timezone";
import type {
  CreditCardSummary,
  MsiSchedulePreview,
  Transaction,
} from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";
import { MsiPreviewLine, MsiSavedInfo, useMsiPreview } from "../wallets/msiSchedule";

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
  const isTransferEdit =
    transaction?.kind === "transfer_in" || transaction?.kind === "transfer_out";
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
  // Editing a transfer needs both legs (from/to wallets, both amounts); the
  // clicked row is only one leg, so fetch the whole pair.
  const transferDetail = useQuery({
    queryKey: ["transfer", transaction?.id],
    queryFn: () => getTransfer(transaction!.id),
    enabled: open && isTransferEdit,
  });

  const [tab, setTab] = useState<Tab>("income");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [toWalletId, setToWalletId] = useState<number | null>(null);
  const [amountText, setAmountText] = useState("");
  const [amountToText, setAmountToText] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(todayIso());
  const [time, setTime] = useState(nowTime(getTimezone()));
  const [msiEnabled, setMsiEnabled] = useState(false);
  const [msiMonthsText, setMsiMonthsText] = useState("12");
  // Saving an MSI plan or a card payment ends on a confirmation screen (the
  // consequences happen later, at the cut), instead of closing silently.
  const [saved, setSaved] = useState<
    | { kind: "msi"; schedule: MsiSchedulePreview; currency: string }
    | { kind: "payment"; summary: CreditCardSummary; currency: string }
    | null
  >(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setToWalletId(null);
    setAmountToText("");
    setMsiEnabled(false);
    setMsiMonthsText("12");
    setSaved(null);
    if (transaction && isTransferEdit) {
      // Edit mode, transfer: note/date/time come from this leg; the from/to
      // wallets and both amounts are filled once getTransfer resolves (below).
      setTab("transfer");
      setCategoryId(null);
      setDescription(transaction.description ?? "");
      setDate(transaction.occurredAt);
      setTime(timeInputValue(transaction.occurredTime, transaction.createdAt, getTimezone()));
    } else if (transaction) {
      // Edit mode: prefill from the existing income/expense.
      setTab(transaction.kind === "expense" ? "expense" : "income");
      setWalletId(transaction.walletId);
      setAmountText((transaction.amountCents / 100).toFixed(2));
      setCategoryId(transaction.categoryId);
      setDescription(transaction.description ?? "");
      setDate(transaction.occurredAt);
      setTime(timeInputValue(transaction.occurredTime, transaction.createdAt, getTimezone()));
    } else {
      setTab("income");
      setWalletId(defaultWalletId ?? null);
      setAmountText("");
      setCategoryId(null);
      setDescription("");
      setDate(todayIso());
      setTime(nowTime(getTimezone()));
    }
  }, [open, defaultWalletId, transaction, isTransferEdit]);

  // Fill the from/to wallets and both amounts once the transfer's legs load.
  useEffect(() => {
    if (!open || !isTransferEdit) return;
    const d = transferDetail.data;
    if (!d) return;
    setWalletId(d.fromWalletId);
    setToWalletId(d.toWalletId);
    setAmountText((d.amountFromCents / 100).toFixed(2));
    setAmountToText((d.amountToCents / 100).toFixed(2));
  }, [open, isTransferEdit, transferDetail.data]);

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
  const msiPreview = useMsiPreview(
    fromWallet?.id,
    amountText,
    msiMonthsText,
    date,
    msiActive && !saved,
  );

  // Transferring INTO a configured credit card is paying it: show how the
  // statement stands so the user knows how much clears it interest-free.
  const effectiveToId =
    toWalletId ?? walletList.find((w) => w.id !== (walletId ?? walletList[0]?.id))?.id;
  const effectiveToWallet = walletList.find((w) => w.id === effectiveToId);
  const payingCard =
    !isEdit && tab === "transfer" && effectiveToWallet?.creditCutDay != null;
  const cardSummary = useQuery({
    queryKey: ["creditCard", effectiveToId],
    queryFn: () => getCreditCardSummary(effectiveToId!),
    enabled: payingCard,
  });

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
        occurredTime: time === "" ? null : time,
      };
      // Edits go straight to the server (online-only, like delete); no outbox.
      if (transaction && tab === "transfer") {
        const toId = toWalletId ?? walletList.find((w) => w.id !== wid)?.id;
        if (toId === undefined) throw new Error(es.transactions.toWallet);
        const toCents = crossCurrency ? parseToCents(amountToText) : cents;
        if (toCents === null || toCents <= 0)
          throw new Error(es.transactions.invalidAmount);
        return updateTransfer(transaction.id, {
          fromWalletId: wid,
          toWalletId: toId,
          amountFromCents: cents,
          amountToCents: toCents,
          description: common.description,
          occurredAt: date,
          occurredTime: common.occurredTime,
        });
      }
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
          categoryId,
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
        occurredTime: common.occurredTime,
      });
    },
    onSuccess: async (data) => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["creditCard"] });
      if (msiActive && data) {
        setSaved({
          kind: "msi",
          schedule: data as MsiSchedulePreview,
          currency: fromWallet?.currencyCode ?? "MXN",
        });
        return;
      }
      if (payingCard && effectiveToId !== undefined) {
        // Re-read the statement AFTER the payment so the confirmation says
        // where the card actually stands ("liquidado" / "te faltan $X").
        try {
          const summary = await getCreditCardSummary(effectiveToId);
          setSaved({
            kind: "payment",
            summary,
            currency: effectiveToWallet?.currencyCode ?? "MXN",
          });
          return;
        } catch {
          // Offline or transient failure: fall back to closing quietly.
        }
      }
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  if (saved) {
    const closeAll = () => {
      setSaved(null);
      onClose();
    };
    const paymentLine =
      saved.kind === "payment"
        ? saved.summary.statement.remainingCents > 0
          ? es.credit.paySavedRemaining
              .replace(
                "{amount}",
                formatCents(saved.summary.statement.remainingCents, saved.currency),
              )
              .replace("{date}", formatDayMonth(saved.summary.statement.dueDate))
          : es.credit.paySavedDone
        : null;
    return (
      <Modal
        title={saved.kind === "msi" ? es.credit.msiSavedTitle : es.credit.paySavedTitle}
        open={open}
        onClose={closeAll}
      >
        <div className="grid gap-4">
          {saved.kind === "msi" ? (
            <MsiSavedInfo preview={saved.schedule} currency={saved.currency} />
          ) : (
            <p className="rounded-lg bg-surface-overlay px-3 py-2.5 text-sm text-fg-muted">
              {paymentLine}
            </p>
          )}
          <div className="flex justify-end">
            <Button onClick={closeAll}>{es.credit.understood}</Button>
          </div>
        </div>
      </Modal>
    );
  }

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
            {payingCard && cardSummary.data && (
              <span className="mt-1 block text-xs text-fg-subtle">
                {cardSummary.data.statement.remainingCents > 0
                  ? es.credit.payContext
                      .replace(
                        "{amount}",
                        formatCents(
                          cardSummary.data.statement.remainingCents,
                          effectiveToWallet?.currencyCode ?? "MXN",
                        ),
                      )
                      .replace(
                        "{date}",
                        formatDayMonth(cardSummary.data.statement.dueDate),
                      )
                  : es.credit.payContextPaid}
              </span>
            )}
          </Field>
        )}

        <Field label={es.transactions.amount}>
          <MoneyInput value={amountText} onChange={setAmountText} required autoFocus />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label={es.transactions.date}>
            <DateInput value={date} onChange={setDate} />
          </Field>
          <Field label={es.transactions.time}>
            <TimeInput value={time} onChange={setTime} />
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
            installment plan. The chosen category (below, same picker as any
            expense) is what the monthly charges file under. */}
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
              <div className="mt-3 space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <Field label={es.credit.msiMonths}>
                    <input
                      className={inputClass}
                      value={msiMonthsText}
                      onChange={(e) => setMsiMonthsText(e.target.value)}
                      inputMode="numeric"
                    />
                  </Field>
                </div>
                <MsiPreviewLine
                  preview={msiPreview}
                  currency={fromWallet?.currencyCode ?? "MXN"}
                />
              </div>
            )}
          </div>
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
                  {seedName(c.name, c.isSystem)}
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
            required={msiActive}
          />
        </Field>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button
            type="submit"
            disabled={
              mutation.isPending ||
              walletList.length === 0 ||
              (isTransferEdit && !transferDetail.data)
            }
          >
            {es.common.save}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
