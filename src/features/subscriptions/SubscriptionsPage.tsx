import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { CreditCard, Pause, Pencil, Play, Plus, Receipt, Trash2 } from "lucide-react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { PageHeader } from "../../components/PageHeader";
import {
  createSubscription,
  deleteSubscription,
  listCurrencies,
  listSubscriptions,
  listTransactionCategories,
  listWallets,
  registerSubscriptionPayment,
  setSubscriptionActive,
  updateSubscription,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import type { Subscription } from "../../lib/types";
import { es } from "../../i18n/es";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function SubscriptionsPage() {
  const qc = useQueryClient();
  const subs = useQuery({ queryKey: ["subscriptions"], queryFn: listSubscriptions });
  const [formSub, setFormSub] = useState<Subscription | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["subscriptions"] });
    qc.invalidateQueries({ queryKey: ["dashboard"] });
    qc.invalidateQueries({ queryKey: ["transactions"] });
  };
  const remove = useMutation({ mutationFn: deleteSubscription, onSuccess: invalidate });
  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      setSubscriptionActive(id, active),
    onSuccess: invalidate,
  });
  const pay = useMutation({ mutationFn: registerSubscriptionPayment, onSuccess: invalidate });

  const data = subs.data;
  const list = data?.subscriptions ?? [];

  return (
    <>
      <PageHeader
        title={es.subscriptions.title}
        actions={
          <Button
            onClick={() => {
              setFormSub(null);
              setFormOpen(true);
            }}
          >
            <span className="flex items-center gap-2">
              <Plus size={16} /> {es.subscriptions.newSubscription}
            </span>
          </Button>
        }
      />

      {data && list.length > 0 && (
        <p className="mb-4 text-sm text-fg-muted">
          {es.subscriptions.monthlyTotal}:{" "}
          <span className="font-display text-base font-semibold tabular-nums text-fg">
            {formatCents(data.monthlyTotalMxnCents, "MXN")}
          </span>
        </p>
      )}

      {subs.isSuccess && list.length === 0 && (
        <EmptyState
          icon={CreditCard}
          title={es.subscriptions.emptyTitle}
          description={es.subscriptions.emptyDescription}
        />
      )}

      <div className="grid gap-3">
        {list.map((sub, i) => {
          const color = sub.color ?? CHART_COLORS[i % CHART_COLORS.length];
          return (
            <section
              key={sub.id}
              className={`flex flex-wrap items-center gap-3 rounded-2xl border border-border-muted bg-surface-raised p-4 shadow-card ${
                sub.isActive ? "" : "opacity-60"
              }`}
            >
              <span
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-base font-semibold text-white"
                style={{ backgroundColor: color }}
              >
                {sub.name.charAt(0).toUpperCase()}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-fg">{sub.name}</p>
                <p className="text-xs text-fg-subtle">
                  {sub.cadence === "yearly" ? es.subscriptions.yearly : es.subscriptions.monthly}
                  {" · "}
                  {es.subscriptions.nextCharge}: {sub.nextChargeDate}
                </p>
              </div>
              <span className="shrink-0 tabular-nums font-medium text-fg">
                {formatCents(sub.amountCents, sub.currencyCode)}
              </span>
              <div className="flex shrink-0 gap-1">
                <button
                  title={es.subscriptions.registerPayment}
                  disabled={!sub.walletId || pay.isPending}
                  onClick={() => pay.mutate(sub.id)}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-accent disabled:opacity-40"
                >
                  <Receipt size={16} />
                </button>
                <button
                  title={sub.isActive ? es.subscriptions.pause : es.subscriptions.resume}
                  onClick={() => toggle.mutate({ id: sub.id, active: !sub.isActive })}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
                >
                  {sub.isActive ? <Pause size={16} /> : <Play size={16} />}
                </button>
                <button
                  title={es.common.edit}
                  onClick={() => {
                    setFormSub(sub);
                    setFormOpen(true);
                  }}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
                >
                  <Pencil size={16} />
                </button>
                <button
                  title={es.common.delete}
                  onClick={() => setDeleteId(sub.id)}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </section>
          );
        })}
      </div>

      <SubscriptionFormModal
        open={formOpen}
        sub={formSub}
        onClose={() => setFormOpen(false)}
        onSaved={invalidate}
      />
      <ConfirmDialog
        open={deleteId !== null}
        title={es.common.delete}
        message={es.subscriptions.deleteConfirm}
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

function SubscriptionFormModal({
  open,
  sub,
  onClose,
  onSaved,
}: {
  open: boolean;
  sub: Subscription | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const currencies = useQuery({ queryKey: ["currencies"], queryFn: listCurrencies });
  const wallets = useQuery({ queryKey: ["wallets", { showArchived: false }], queryFn: () => listWallets(false) });
  const categories = useQuery({
    queryKey: ["transactionCategories"],
    queryFn: listTransactionCategories,
  });

  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("MXN");
  const [cadence, setCadence] = useState<"monthly" | "yearly">("monthly");
  const [nextDate, setNextDate] = useState(today());
  const [walletId, setWalletId] = useState<string>("");
  const [categoryId, setCategoryId] = useState<string>("");
  const [color, setColor] = useState<string>(CHART_COLORS[0]);
  const [error, setError] = useState<string | null>(null);

  const [lastKey, setLastKey] = useState("");
  const key = `${open}-${sub?.id ?? "new"}`;
  if (open && key !== lastKey) {
    setLastKey(key);
    setName(sub?.name ?? "");
    setAmount(sub ? (sub.amountCents / 100).toString() : "");
    setCurrency(sub?.currencyCode ?? "MXN");
    setCadence(sub?.cadence ?? "monthly");
    setNextDate(sub?.nextChargeDate ?? today());
    setWalletId(sub?.walletId != null ? String(sub.walletId) : "");
    setCategoryId(sub?.categoryId != null ? String(sub.categoryId) : "");
    setColor(sub?.color ?? CHART_COLORS[0]);
    setError(null);
  }

  const expenseCats = (categories.data ?? []).filter((c) => c.kind === "expense");

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(amount);
      if (!name.trim() || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      const input = {
        name: name.trim(),
        icon: null,
        color,
        amountCents: cents,
        currencyCode: currency,
        cadence,
        nextChargeDate: nextDate,
        walletId: walletId === "" ? null : Number(walletId),
        categoryId: categoryId === "" ? null : Number(categoryId),
      };
      return sub ? updateSubscription(sub.id, input) : createSubscription(input);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={sub ? es.subscriptions.editSubscription : es.subscriptions.newSubscription}
    >
      <div className="flex flex-col gap-4">
        <Field label={es.subscriptions.name}>
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.subscriptions.amount}>
            <input
              className={inputClass}
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
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
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.subscriptions.cadence}>
            <select
              className={inputClass}
              value={cadence}
              onChange={(e) => setCadence(e.target.value as "monthly" | "yearly")}
            >
              <option value="monthly">{es.subscriptions.monthly}</option>
              <option value="yearly">{es.subscriptions.yearly}</option>
            </select>
          </Field>
          <Field label={es.subscriptions.nextCharge}>
            <DateInput value={nextDate} onChange={setNextDate} />
          </Field>
        </div>
        <Field label={es.subscriptions.wallet}>
          <select
            className={inputClass}
            value={walletId}
            onChange={(e) => setWalletId(e.target.value)}
          >
            <option value="">{es.subscriptions.none}</option>
            {(wallets.data ?? []).map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label={es.subscriptions.category}>
          <select
            className={inputClass}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">{es.subscriptions.none}</option>
            {expenseCats.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label={es.common.color}>
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
