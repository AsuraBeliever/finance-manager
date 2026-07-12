import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Modal } from "../../../components/Modal";
import { getCategoryTransactions, listWallets } from "../../../lib/api";
import { transactionTime } from "../../../lib/date";
import { formatCents } from "../../../lib/money";
import { useClock } from "../../../lib/timeFormat";
import { useTimezone } from "../../../lib/timezone";
import type { Period } from "../../../lib/types";
import { es } from "../../../i18n/es";
import { seedName } from "../../../i18n/seed";

/** The category slice the user tapped: identifies the drill-down query and
 *  supplies the MXN total shown in the header (matches the donut). */
export interface CategoryDetailTarget {
  categoryId: number | null;
  name: string;
  mxnCents: number;
}

const meta = {
  income: { icon: ArrowDownLeft, sign: "+", color: "text-accent" },
  expense: { icon: ArrowUpRight, sign: "−", color: "text-danger" },
} as const;

/** Read-only list of the transactions behind one breakdown slice, over the same
 *  period. Amounts render in each row's own wallet currency. */
export function CategoryDetailModal({
  kind,
  period,
  target,
  onClose,
}: {
  kind: "income" | "expense";
  period: Period;
  target: CategoryDetailTarget | null;
  onClose: () => void;
}) {
  const tz = useTimezone();
  const clock = useClock();
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const currencyByWallet = useMemo(
    () => new Map((wallets.data ?? []).map((w) => [w.id, w.currencyCode])),
    [wallets.data],
  );
  const q = useQuery({
    queryKey: ["categoryTransactions", kind, target?.categoryId ?? "none", period],
    queryFn: () => getCategoryTransactions(kind, target!.categoryId, period),
    enabled: target !== null,
  });

  const rows = q.data ?? [];
  const { icon: Icon, sign, color } = meta[kind];
  const countLabel =
    rows.length === 1
      ? es.dashboard.movementOne
      : es.dashboard.movementsCount.replace("{n}", String(rows.length));

  return (
    <Modal
      title={target ? seedName(target.name) : ""}
      open={target !== null}
      onClose={onClose}
      solid
      fixedHeight
    >
      {target && (
        <div className="flex flex-col gap-4">
          <div className="flex items-baseline justify-between border-b border-border-muted pb-3">
            <span className="text-sm text-fg-subtle">{countLabel}</span>
            <span className="font-display text-lg font-semibold tabular-nums text-fg">
              {formatCents(target.mxnCents, "MXN")}
            </span>
          </div>

          {q.isLoading ? (
            <p className="py-6 text-center text-sm text-fg-subtle">{es.common.loading}</p>
          ) : rows.length === 0 ? (
            <p className="py-6 text-center text-sm text-fg-subtle">
              {es.dashboard.noPeriodData}
            </p>
          ) : (
            <ul className="divide-y divide-border-muted">
              {rows.map((t) => {
                const time = transactionTime(t.occurredTime, t.createdAt, tz, clock);
                return (
                  <li key={t.id} className="flex items-center gap-3 py-2.5">
                    <span
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-surface-overlay ${color}`}
                    >
                      <Icon size={13} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm text-fg">
                        {t.description || seedName(target.name)}
                      </p>
                      <p className="text-xs text-fg-subtle">
                        {t.occurredAt}
                        {time && <> · {time}</>} · {t.walletName}
                      </p>
                    </div>
                    <span className={`text-sm font-medium tabular-nums ${color}`}>
                      {sign}
                      {formatCents(t.amountCents, currencyByWallet.get(t.walletId) ?? "MXN")}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </Modal>
  );
}
