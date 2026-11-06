import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ClipboardPaste,
  CreditCard,
  Inbox,
  Landmark,
  Mail,
  Trash2,
  TriangleAlert,
  X,
} from "lucide-react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { MoneyInput } from "../../components/MoneyInput";
import { PageHeader } from "../../components/PageHeader";
import { TimeInput } from "../../components/TimeInput";
import {
  GMAIL_CONNECT_URL,
  addBankSender,
  confirmPendingTransaction,
  deleteBankSender,
  disconnectGmail,
  dismissPendingTransaction,
  getBankNoticeSettings,
  importBankNotice,
  listPendingTransactions,
  listTransactionCategories,
  listWallets,
  setCardWallet,
  updateBankSender,
} from "../../lib/api";
import { formatDayMonth, formatTime } from "../../lib/date";
import { useMoney } from "../../lib/hideBalance";
import { parseToCents } from "../../lib/money";
import { useClock } from "../../lib/timeFormat";
import type { IngestResult, PendingTransaction, Wallet } from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";

const PENDING_KEY = ["pendingTransactions"];
const SETTINGS_KEY = ["bankNoticeSettings"];

export function BankNoticesPage() {
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const gmailResult = params.get("gmail");
  const pending = useQuery({ queryKey: PENDING_KEY, queryFn: listPendingTransactions });
  const wallets = useQuery({
    queryKey: ["wallets", { showArchived: false }],
    queryFn: () => listWallets(false),
  });
  const [confirming, setConfirming] = useState<PendingTransaction | null>(null);
  const [dismissId, setDismissId] = useState<number | null>(null);
  const [pasteOpen, setPasteOpen] = useState(false);

  const dismiss = useMutation({
    mutationFn: dismissPendingTransaction,
    onSuccess: () => qc.invalidateQueries({ queryKey: PENDING_KEY }),
  });

  const list = pending.data ?? [];
  const walletName = (id: number | null) =>
    (wallets.data ?? []).find((w) => w.id === id)?.name ?? null;

  const resultBanner =
    gmailResult === "ok"
      ? { ok: true, text: es.bankNotices.gmailOk }
      : gmailResult === "denied"
        ? { ok: false, text: es.bankNotices.gmailDenied }
        : gmailResult === "error"
          ? { ok: false, text: es.bankNotices.gmailError }
          : null;

  return (
    <>
      <PageHeader
        title={es.bankNotices.title}
        actions={
          <Button onClick={() => setPasteOpen(true)}>
            <span className="flex items-center gap-2">
              <ClipboardPaste size={16} /> {es.bankNotices.paste}
            </span>
          </Button>
        }
      />

      {resultBanner && (
        <div
          className={`mb-4 flex items-start gap-2 rounded-xl border p-3 text-sm ${
            resultBanner.ok
              ? "border-accent/30 bg-accent/10 text-fg"
              : "border-danger/30 bg-danger/10 text-fg"
          }`}
        >
          <span className="flex-1">{resultBanner.text}</span>
          <button
            title={es.common.close}
            onClick={() => setParams({}, { replace: true })}
            className="text-fg-subtle hover:text-fg"
          >
            <X size={16} />
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <div className="flex min-w-0 flex-col gap-3">
          {pending.isSuccess && list.length === 0 && (
            <EmptyState
              icon={Inbox}
              title={es.bankNotices.emptyTitle}
              description={es.bankNotices.emptyDescription}
            />
          )}
          {list.map((p) => (
            <PendingCard
              key={p.id}
              p={p}
              walletName={walletName(p.suggestedWalletId)}
              onConfirm={() => setConfirming(p)}
              onDismiss={() => setDismissId(p.id)}
            />
          ))}
        </div>

        <div className="flex min-w-0 flex-col gap-6">
          <GmailPanel />
          <BanksPanel wallets={wallets.data ?? []} />
          <CardsPanel wallets={wallets.data ?? []} />
        </div>
      </div>

      <ConfirmModal
        pending={confirming}
        wallets={wallets.data ?? []}
        onClose={() => setConfirming(null)}
      />
      <PasteModal open={pasteOpen} onClose={() => setPasteOpen(false)} />
      <ConfirmDialog
        open={dismissId !== null}
        title={es.bankNotices.dismiss}
        message={es.bankNotices.dismissConfirm}
        confirmLabel={es.bankNotices.dismiss}
        onConfirm={() => {
          if (dismissId !== null) dismiss.mutate(dismissId);
          setDismissId(null);
        }}
        onClose={() => setDismissId(null)}
      />
    </>
  );
}

function PendingCard({
  p,
  walletName,
  onConfirm,
  onDismiss,
}: {
  p: PendingTransaction;
  walletName: string | null;
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  const money = useMoney();
  const clock = useClock();
  const income = p.kind === "income";
  const Icon = income ? ArrowDownLeft : ArrowUpRight;
  const title = p.merchant ?? p.subject ?? p.sender;
  const meta = [
    p.bankName ?? p.sender,
    p.cardLast4 ? `${es.bankNotices.card} •• ${p.cardLast4}` : null,
    `${formatDayMonth(p.occurredAt)}${p.occurredTime ? ` ${formatTime(p.occurredTime, clock)}` : ""}`,
  ].filter(Boolean);

  return (
    <section
      data-testid="pending-card"
      className="rounded-2xl border border-border-muted bg-surface-raised p-4 shadow-card"
    >
      <div className="flex items-start gap-3">
        <span
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${
            income ? "bg-accent/15 text-accent" : "bg-danger/10 text-danger"
          }`}
        >
          <Icon size={20} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-fg">{title}</p>
          <p className="truncate text-xs text-fg-subtle">{meta.join(" · ")}</p>
          <p className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px]">
            <span className="rounded-full bg-surface-overlay px-2 py-0.5 text-fg-muted">
              {income ? es.bankNotices.income : es.bankNotices.expense}
            </span>
            <span className="rounded-full bg-surface-overlay px-2 py-0.5 text-fg-muted">
              {p.source === "gmail" ? es.bankNotices.viaGmail : es.bankNotices.viaPaste}
            </span>
            {walletName && (
              <span className="rounded-full bg-accent/10 px-2 py-0.5 text-accent">
                {walletName}
              </span>
            )}
          </p>
        </div>
        <span
          className={`shrink-0 tabular-nums font-semibold ${income ? "text-accent" : "text-fg"}`}
        >
          {income ? "+" : "−"}
          {money(p.amountCents, p.currencyCode)}
        </span>
      </div>
      {p.duplicateOf && (
        <p className="mt-3 flex items-start gap-1.5 rounded-lg bg-gold/10 px-3 py-2 text-xs text-fg">
          <TriangleAlert size={14} className="mt-px shrink-0 text-gold" />
          <span>
            {es.bankNotices.duplicate}: {p.duplicateOf.description ?? "—"} ·{" "}
            {p.duplicateOf.walletName} · {formatDayMonth(p.duplicateOf.occurredAt)}
          </span>
        </p>
      )}
      <div className="mt-3 flex justify-end gap-2">
        <Button variant="ghost" onClick={onDismiss}>
          {es.bankNotices.dismiss}
        </Button>
        <Button onClick={onConfirm}>{es.bankNotices.confirm}</Button>
      </div>
    </section>
  );
}

function ConfirmModal({
  pending,
  wallets,
  onClose,
}: {
  pending: PendingTransaction | null;
  wallets: Wallet[];
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const categories = useQuery({
    queryKey: ["transactionCategories"],
    queryFn: listTransactionCategories,
  });
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: getBankNoticeSettings });
  const [walletId, setWalletId] = useState("");
  const [amount, setAmount] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [description, setDescription] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [lastId, setLastId] = useState<number | null>(null);
  if (pending && pending.id !== lastId) {
    setLastId(pending.id);
    setWalletId(pending.suggestedWalletId != null ? String(pending.suggestedWalletId) : "");
    setAmount((pending.amountCents / 100).toFixed(2));
    setCategoryId(pending.suggestedCategoryId != null ? String(pending.suggestedCategoryId) : "");
    setDescription(pending.merchant ?? "");
    setDate(pending.occurredAt);
    setTime(pending.occurredTime ?? "");
    const mapped = (settings.data?.cards ?? []).some((c) => c.cardLast4 === pending.cardLast4);
    setRemember(pending.cardLast4 != null && !mapped);
    setError(null);
  }
  if (!pending && lastId !== null) setLastId(null);

  const kindCats = (categories.data ?? []).filter((c) => c.kind === pending?.kind);
  const wallet = wallets.find((w) => String(w.id) === walletId);

  const save = useMutation({
    mutationFn: () => {
      if (!pending) return Promise.reject(new Error(""));
      const cents = parseToCents(amount);
      if (cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      if (walletId === "") return Promise.reject(new Error(es.bankNotices.pickWallet));
      return confirmPendingTransaction(pending.id, {
        walletId: Number(walletId),
        amountCents: cents,
        categoryId: categoryId === "" ? null : Number(categoryId),
        description: description.trim() || null,
        occurredAt: date,
        occurredTime: time || null,
        rememberCard: remember,
      });
    },
    onSuccess: () => {
      for (const key of [PENDING_KEY, SETTINGS_KEY, ["transactions"], ["wallets"], ["dashboard"]])
        qc.invalidateQueries({ queryKey: key });
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal open={pending !== null} onClose={onClose} title={es.bankNotices.confirmTitle}>
      {pending && (
        <div className="flex flex-col gap-4">
          <Field label={es.bankNotices.wallet}>
            <select
              data-testid="confirm-wallet"
              className={inputClass}
              value={walletId}
              onChange={(e) => setWalletId(e.target.value)}
            >
              <option value="">{es.bankNotices.pickWallet}</option>
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label={es.bankNotices.amount}>
            <MoneyInput value={amount} onChange={setAmount} />
          </Field>
          {wallet && wallet.currencyCode !== pending.currencyCode && (
            <p className="-mt-2 text-xs text-gold">
              {es.bankNotices.currencyWarning.replace("{currency}", pending.currencyCode)}
            </p>
          )}
          <Field label={es.bankNotices.category}>
            <select
              className={inputClass}
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <option value="">{es.bankNotices.none}</option>
              {kindCats.map((c) => (
                <option key={c.id} value={c.id}>
                  {seedName(c.name, c.isSystem)}
                </option>
              ))}
            </select>
          </Field>
          <Field label={es.bankNotices.description}>
            <input
              className={inputClass}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label={es.bankNotices.date}>
              <DateInput value={date} onChange={setDate} />
            </Field>
            <Field label={es.bankNotices.time}>
              <TimeInput value={time} onChange={setTime} />
            </Field>
          </div>
          {pending.cardLast4 && (
            <label className="flex cursor-pointer items-start gap-2.5 rounded-lg border border-border-muted p-3">
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
                className="mt-0.5 h-4 w-4 accent-accent"
              />
              <span className="text-sm">
                {es.bankNotices.rememberCard.replace("{card}", pending.cardLast4)}
              </span>
            </label>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={onClose}>
              {es.common.cancel}
            </Button>
            <Button
              data-testid="confirm-save"
              onClick={() => save.mutate()}
              disabled={save.isPending}
            >
              {es.bankNotices.confirm}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function PasteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [text, setText] = useState("");
  const [result, setResult] = useState<IngestResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setText("");
    setResult(null);
    setError(null);
    onClose();
  };

  const read = useMutation({
    mutationFn: () => importBankNotice(text),
    onSuccess: (r) => {
      setResult(r);
      setError(null);
      if (r.status === "added") {
        qc.invalidateQueries({ queryKey: PENDING_KEY });
        setText("");
      }
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  const message =
    result?.status === "added"
      ? es.bankNotices.resultAdded
      : result?.status === "duplicate"
        ? es.bankNotices.resultDuplicate
        : result?.status === "rejected"
          ? es.bankNotices.rejected[result.reason]
          : null;

  return (
    <Modal open={open} onClose={close} title={es.bankNotices.pasteTitle}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-fg-muted">{es.bankNotices.pasteHint}</p>
        <textarea
          data-testid="paste-text"
          className={`${inputClass} min-h-40`}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setResult(null);
          }}
          placeholder={es.bankNotices.pastePlaceholder}
        />
        {message && (
          <p className={`text-sm ${result?.status === "added" ? "text-accent" : "text-gold"}`}>
            {message}
          </p>
        )}
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close}>
            {es.common.close}
          </Button>
          <Button
            data-testid="paste-read"
            onClick={() => read.mutate()}
            disabled={read.isPending || text.trim() === ""}
          >
            {es.bankNotices.read}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function GmailPanel() {
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: getBankNoticeSettings });
  const [confirmOff, setConfirmOff] = useState(false);
  const disconnect = useMutation({
    mutationFn: disconnectGmail,
    onSuccess: () => qc.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
  const data = settings.data;
  const gmail = data?.gmail;
  const connect = () => {
    window.location.href = GMAIL_CONNECT_URL;
  };

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 flex items-center gap-2 font-medium">
        <Mail size={16} className="text-fg-subtle" />
        {es.bankNotices.gmailTitle}
      </h3>
      <p className="mb-3 text-xs text-fg-subtle">{es.bankNotices.gmailHint}</p>
      {data && !data.gmailAvailable && !gmail && (
        <p className="text-sm text-fg-muted">{es.bankNotices.gmailUnavailable}</p>
      )}
      {data?.gmailAvailable && !gmail && (
        <Button onClick={connect}>{es.bankNotices.gmailConnect}</Button>
      )}
      {gmail && (
        <div className="flex flex-col gap-2 text-sm">
          <p className="text-fg">{es.bankNotices.gmailConnected.replace("{email}", gmail.email)}</p>
          {gmail.needsReconnect ? (
            <p className="text-danger">{es.bankNotices.gmailNeedsReconnect}</p>
          ) : (
            <p className="text-xs text-fg-subtle">
              {gmail.lastPushAt
                ? es.bankNotices.gmailLastPush.replace("{when}", `${gmail.lastPushAt} UTC`)
                : es.bankNotices.gmailNeverPushed}
            </p>
          )}
          <div className="flex gap-2">
            {gmail.needsReconnect && data?.gmailAvailable && (
              <Button onClick={connect}>{es.bankNotices.gmailReconnect}</Button>
            )}
            <Button variant="danger" onClick={() => setConfirmOff(true)}>
              {es.bankNotices.gmailDisconnect}
            </Button>
          </div>
        </div>
      )}
      <ConfirmDialog
        open={confirmOff}
        title={es.bankNotices.gmailDisconnect}
        message={es.bankNotices.gmailDisconnectConfirm}
        confirmLabel={es.bankNotices.gmailDisconnect}
        onConfirm={() => {
          disconnect.mutate();
          setConfirmOff(false);
        }}
        onClose={() => setConfirmOff(false)}
      />
    </section>
  );
}

function WalletSelect({
  wallets,
  value,
  onChange,
  emptyLabel,
}: {
  wallets: Wallet[];
  value: number | null;
  onChange: (id: number | null) => void;
  emptyLabel: string;
}) {
  return (
    <select
      className={`${inputClass} py-1.5`}
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value === "" ? null : Number(e.target.value))}
    >
      <option value="">{emptyLabel}</option>
      {wallets.map((w) => (
        <option key={w.id} value={w.id}>
          {w.name}
        </option>
      ))}
    </select>
  );
}

function BanksPanel({ wallets }: { wallets: Wallet[] }) {
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: getBankNoticeSettings });
  const [pattern, setPattern] = useState("");
  const [error, setError] = useState<string | null>(null);
  const refresh = () => {
    qc.invalidateQueries({ queryKey: SETTINGS_KEY });
    qc.invalidateQueries({ queryKey: PENDING_KEY });
  };
  const add = useMutation({
    mutationFn: () => addBankSender(pattern, null),
    onSuccess: () => {
      setPattern("");
      setError(null);
      refresh();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });
  const update = useMutation({
    mutationFn: ({ id, walletId }: { id: number; walletId: number | null }) =>
      updateBankSender(id, walletId),
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: deleteBankSender, onSuccess: refresh });
  const data = settings.data;

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 flex items-center gap-2 font-medium">
        <Landmark size={16} className="text-fg-subtle" />
        {es.bankNotices.banksTitle}
      </h3>
      <p className="mb-3 text-xs text-fg-subtle">{es.bankNotices.banksHint}</p>
      <ul className="mb-3 flex flex-col gap-2">
        {data?.knownBanks.map((b) => (
          <li key={b.key} className="flex items-center justify-between gap-2 text-sm">
            <span className="text-fg">{b.name}</span>
            <span className="truncate text-xs text-fg-subtle">
              {b.domains[0]} · {es.bankNotices.builtIn}
            </span>
          </li>
        ))}
        {data?.senders.map((s) => (
          <li key={s.id} className="flex items-center gap-2 text-sm">
            <span className="min-w-0 flex-1 truncate text-fg">{s.pattern}</span>
            <div className="w-36 shrink-0">
              <WalletSelect
                wallets={wallets}
                value={s.walletId}
                onChange={(walletId) => update.mutate({ id: s.id, walletId })}
                emptyLabel={es.bankNotices.senderWallet}
              />
            </div>
            <button
              title={es.common.delete}
              onClick={() => remove.mutate(s.id)}
              className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
            >
              <Trash2 size={15} />
            </button>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <input
          data-testid="sender-pattern"
          className={inputClass}
          value={pattern}
          onChange={(e) => setPattern(e.target.value)}
          placeholder={es.bankNotices.senderPlaceholder}
        />
        <Button
          onClick={() => add.mutate()}
          disabled={add.isPending || pattern.trim() === ""}
        >
          {es.bankNotices.addSender}
        </Button>
      </div>
      {error && <p className="mt-2 text-sm text-danger">{error}</p>}
    </section>
  );
}

function CardsPanel({ wallets }: { wallets: Wallet[] }) {
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: getBankNoticeSettings });
  const [last4, setLast4] = useState("");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const refresh = () => {
    qc.invalidateQueries({ queryKey: SETTINGS_KEY });
    qc.invalidateQueries({ queryKey: PENDING_KEY });
  };
  const save = useMutation({
    mutationFn: ({ card, wallet }: { card: string; wallet: number | null }) =>
      setCardWallet(card, wallet),
    onSuccess: () => {
      setError(null);
      refresh();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });
  const cards = settings.data?.cards ?? [];

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 flex items-center gap-2 font-medium">
        <CreditCard size={16} className="text-fg-subtle" />
        {es.bankNotices.cardsTitle}
      </h3>
      <p className="mb-3 text-xs text-fg-subtle">{es.bankNotices.cardsHint}</p>
      {cards.length === 0 && <p className="mb-3 text-sm text-fg-muted">{es.bankNotices.noCards}</p>}
      <ul className="mb-3 flex flex-col gap-2">
        {cards.map((c) => (
          <li key={c.cardLast4} className="flex items-center gap-2 text-sm">
            <span className="w-16 tabular-nums text-fg">•• {c.cardLast4}</span>
            <div className="min-w-0 flex-1">
              <WalletSelect
                wallets={wallets}
                value={c.walletId}
                onChange={(wallet) => save.mutate({ card: c.cardLast4, wallet })}
                emptyLabel={es.bankNotices.pickWallet}
              />
            </div>
            <button
              title={es.common.delete}
              onClick={() => save.mutate({ card: c.cardLast4, wallet: null })}
              className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
            >
              <Trash2 size={15} />
            </button>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <input
          className={`${inputClass} !w-20 shrink-0`}
          inputMode="numeric"
          maxLength={4}
          value={last4}
          onChange={(e) => setLast4(e.target.value.replace(/\D/g, ""))}
          placeholder={es.bankNotices.cardPlaceholder}
        />
        <div className="min-w-0 flex-1">
          <WalletSelect
            wallets={wallets}
            value={walletId}
            onChange={setWalletId}
            emptyLabel={es.bankNotices.pickWallet}
          />
        </div>
        <Button
          onClick={() => {
            save.mutate({ card: last4, wallet: walletId });
            setLast4("");
          }}
          disabled={last4.length !== 4 || walletId === null}
        >
          {es.bankNotices.addCard}
        </Button>
      </div>
      {error && <p className="mt-2 text-sm text-danger">{error}</p>}
    </section>
  );
}
