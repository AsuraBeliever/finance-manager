import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CreditCard, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { MoneyInput } from "../../components/MoneyInput";
import { ProgressBar } from "../../components/ProgressBar";
import {
  createMsiPlan,
  deleteMsiPlan,
  getCreditCardSummary,
  type MsiPlanInput,
} from "../../lib/api";
import { todayIso } from "../../lib/date";
import { formatCents, parseToCents } from "../../lib/money";
import type { MsiPlan, Wallet } from "../../lib/types";
import { es } from "../../i18n/es";

/** Short locale-aware date like "5 jul" (year only when it differs). */
function formatDay(iso: string): string {
  const d = new Date(`${iso}T00:00:00`);
  const opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString(undefined, opts);
}

/** "hoy" / "mañana" / "en N días" for upcoming dates. */
function inDays(days: number): string {
  if (days <= 0) return es.credit.today;
  if (days === 1) return es.credit.tomorrow;
  return es.credit.inDays.replace("{days}", String(days));
}

/** Utilization traffic light: jade under 30%, amber under 70%, danger above —
 *  the same thresholds credit bureaus care about. */
function usageColor(fraction: number): string {
  if (fraction < 0.3) return "#34d399";
  if (fraction < 0.7) return "#f59e0b";
  return "#ef4444";
}

export function CreditCardPanel({ wallet }: { wallet: Wallet }) {
  const queryClient = useQueryClient();
  const [msiFormOpen, setMsiFormOpen] = useState(false);
  const [deletingPlan, setDeletingPlan] = useState<MsiPlan | null>(null);

  const summary = useQuery({
    queryKey: ["creditCard", wallet.id],
    queryFn: () => getCreditCardSummary(wallet.id),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["creditCard", wallet.id] });
    queryClient.invalidateQueries({ queryKey: ["transactions"] });
    queryClient.invalidateQueries({ queryKey: ["wallets"] });
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const removePlan = useMutation({
    mutationFn: (id: number) => deleteMsiPlan(id),
    onSuccess: () => {
      setDeletingPlan(null);
      invalidate();
    },
  });

  if (summary.isPending || summary.isError) return null;
  const s = summary.data;
  const cur = wallet.currencyCode;

  const st = s.statement;
  const statementLine =
    st.balanceCents === 0
      ? es.credit.noStatementDebt
      : st.remainingCents === 0
        ? es.credit.statementPaid
        : st.daysToDue < 0
          ? es.credit.overdueBy.replace("{days}", String(-st.daysToDue))
          : st.daysToDue === 0
            ? es.credit.dueToday
            : es.credit.payBy
                .replace("{amount}", formatCents(st.remainingCents, cur))
                .replace("{date}", formatDay(st.dueDate));
  const statementUrgent = st.remainingCents > 0 && st.daysToDue <= 3;

  const usage =
    s.creditLimitCents != null && s.utilizationBps != null
      ? s.utilizationBps / 10000
      : null;

  return (
    <div className="mb-6 rounded-xl border border-border-muted bg-surface-raised p-5">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="flex items-center gap-2 font-medium">
          <CreditCard size={16} className="text-accent" />
          {es.credit.title}
        </h3>
        <span className="text-xs text-fg-subtle">
          {es.credit.nextCut}: {formatDay(s.nextCutDate)} · {inDays(s.daysToCut)}
        </span>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <p className="text-xs uppercase tracking-[0.12em] text-fg-subtle">
            {es.credit.debt}
          </p>
          <p
            className={`mt-1 text-2xl font-semibold tabular-nums ${
              s.debtCents > 0 ? "text-danger" : ""
            }`}
          >
            {formatCents(s.debtCents, cur)}
          </p>
          {s.pendingMsiCents > 0 && (
            <p className="mt-0.5 text-xs text-fg-subtle">
              {es.credit.msiPendingTotal}: {formatCents(s.pendingMsiCents, cur)}
            </p>
          )}
        </div>

        {s.availableCreditCents != null && (
          <div>
            <p className="text-xs uppercase tracking-[0.12em] text-fg-subtle">
              {es.credit.availableCredit}
            </p>
            <p className="mt-1 text-2xl font-semibold tabular-nums">
              {formatCents(s.availableCreditCents, cur)}
            </p>
          </div>
        )}
      </div>

      {/* Last statement: what to pay and by when to stay interest-free. */}
      <div
        className={`mt-4 rounded-lg px-3 py-2.5 text-sm ${
          statementUrgent
            ? "bg-danger/10 text-danger"
            : "bg-surface-overlay text-fg-muted"
        }`}
      >
        <p className="text-xs text-fg-subtle">
          {es.credit.statement} ({es.credit.statementOf.replace("{date}", formatDay(st.cutDate))}
          ): <span className="tabular-nums">{formatCents(st.balanceCents, cur)}</span>
          {st.paidCents > 0 && st.balanceCents > 0 && (
            <>
              {" "}
              · {es.credit.paidSoFar}:{" "}
              <span className="tabular-nums">{formatCents(st.paidCents, cur)}</span>
            </>
          )}
        </p>
        <p className="mt-1 font-medium">{statementLine}</p>
      </div>

      {usage != null && (
        <div className="mt-4">
          <div className="mb-1.5 flex items-center justify-between text-xs">
            <span className="uppercase tracking-[0.12em] text-fg-subtle">
              {es.credit.utilization}
            </span>
            <span className="tabular-nums text-fg-muted">
              {Math.round(usage * 100)}% ·{" "}
              {es.credit.utilizationOf
                .replace("{used}", formatCents(s.debtCents + s.pendingMsiCents, cur))
                .replace("{limit}", formatCents(s.creditLimitCents!, cur))}
            </span>
          </div>
          <ProgressBar value={usage} color={usageColor(usage)} />
        </div>
      )}

      {s.nextAnniversary && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-fg-subtle">
          <CalendarClock size={13} />
          {es.credit.nextAnniversary}: {formatDay(s.nextAnniversary)}
        </p>
      )}

      {/* MSI plans */}
      <div className="mt-5 border-t border-border-muted pt-4">
        <div className="mb-2 flex items-center justify-between">
          <h4 className="text-sm font-medium">{es.credit.msiTitle}</h4>
          <Button variant="ghost" onClick={() => setMsiFormOpen(true)}>
            <span className="flex items-center gap-2">
              <Plus size={15} /> {es.credit.msiAdd}
            </span>
          </Button>
        </div>

        {s.msiPlans.length === 0 ? (
          <p className="text-xs text-fg-subtle">{es.credit.msiEmpty}</p>
        ) : (
          <ul className="space-y-2">
            {s.msiPlans.map((p) => {
              const done = p.billedMonths >= p.months;
              return (
                <li
                  key={p.id}
                  className="rounded-lg bg-surface-overlay px-3 py-2.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="min-w-0 truncate text-sm font-medium">{p.description}</p>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="text-sm tabular-nums text-fg-muted">
                        {es.credit.msiMonthly.replace(
                          "{amount}",
                          formatCents(p.monthlyCents, cur),
                        )}
                      </span>
                      <button
                        type="button"
                        title={es.common.delete}
                        onClick={() => setDeletingPlan(p)}
                        className="text-fg-subtle transition-colors hover:text-danger"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                  <ProgressBar
                    className="mt-2"
                    value={p.billedMonths / p.months}
                    segments={p.months <= 24 ? p.months : undefined}
                  />
                  <p className="mt-1.5 text-xs text-fg-subtle">
                    {es.credit.msiProgress
                      .replace("{billed}", String(p.billedMonths))
                      .replace("{months}", String(p.months))}
                    {" · "}
                    {done
                      ? es.credit.msiDone
                      : p.nextChargeDate &&
                        es.credit.msiNextCharge
                          .replace("{amount}", formatCents(p.nextChargeCents ?? 0, cur))
                          .replace("{date}", formatDay(p.nextChargeDate))}
                  </p>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <MsiFormModal
        open={msiFormOpen}
        walletId={wallet.id}
        onClose={() => setMsiFormOpen(false)}
        onSaved={invalidate}
      />
      <ConfirmDialog
        open={deletingPlan !== null}
        title={es.credit.msiDeleteTitle}
        message={es.credit.msiDeleteMessage}
        onConfirm={() => deletingPlan && removePlan.mutate(deletingPlan.id)}
        onClose={() => setDeletingPlan(null)}
      />
    </div>
  );
}

function MsiFormModal({
  open,
  walletId,
  onClose,
  onSaved,
}: {
  open: boolean;
  walletId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [description, setDescription] = useState("");
  const [totalText, setTotalText] = useState("");
  const [monthsText, setMonthsText] = useState("12");
  const [purchasedAt, setPurchasedAt] = useState(todayIso());
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (input: MsiPlanInput) => createMsiPlan(input),
    onSuccess: () => {
      onSaved();
      onClose();
      setDescription("");
      setTotalText("");
      setMonthsText("12");
      setPurchasedAt(todayIso());
      setError(null);
    },
    onError: (e) => setError(String(e)),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const totalCents = parseToCents(totalText);
    if (totalCents === null || totalCents <= 0) {
      setError(es.wallets.invalidAmount);
      return;
    }
    const months = parseInt(monthsText, 10);
    if (!isFinite(months) || months < 2 || months > 60) {
      setError(es.credit.msiInvalidMonths);
      return;
    }
    mutation.mutate({
      walletId,
      description: description.trim(),
      totalCents,
      months,
      purchasedAt,
    });
  }

  return (
    <Modal title={es.credit.msiAdd} open={open} onClose={onClose}>
      <form onSubmit={submit} className="grid gap-4">
        <Field label={es.transactions.description}>
          <input
            className={inputClass}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            autoFocus
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.credit.msiTotal}>
            <MoneyInput value={totalText} onChange={setTotalText} />
          </Field>
          <Field label={es.credit.msiMonths}>
            <input
              className={inputClass}
              value={monthsText}
              onChange={(e) => setMonthsText(e.target.value)}
              inputMode="numeric"
            />
          </Field>
        </div>
        <Field label={es.credit.msiPurchasedAt}>
          <DateInput value={purchasedAt} onChange={setPurchasedAt} />
          <span className="mt-1 block text-xs text-fg-subtle">
            {es.credit.msiBackdatedHint}
          </span>
        </Field>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {es.common.save}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
