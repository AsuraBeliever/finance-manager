import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowUpRight, Pencil, Trash2, TrendingUp } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { PeriodPicker } from "../../components/PeriodPicker";
import { YieldToggle } from "../../components/YieldToggle";
import { listInvestmentActivity } from "../../lib/api";
import { useMoney } from "../../lib/hideBalance";
import type { Period } from "../../lib/types";
import { es } from "../../i18n/es";

/** Rows per page; «Mostrar más» asks for this many more. */
const PAGE = 100;

interface Props {
  investmentId: number;
  /** Deposits and withdrawals can be logged (every calculator but manual). */
  canMove: boolean;
  onAdd: (kind: "deposit" | "withdrawal") => void;
  onEdit: (movementId: number) => void;
  onDelete: (movementId: number) => void;
}

/** The investment's deposits, withdrawals and daily yield in one table
 *  (FIN-12), filtered by date window and by the «Rendimientos» switch like the
 *  transactions list, with what the filter adds up to. Rows and totals come
 *  from the worker; this only lays them out. */
export function InvestmentActivity({ investmentId, canMove, onAdd, onEdit, onDelete }: Props) {
  const money = useMoney();
  const [period, setPeriod] = useState<Period | null>(null);
  const [showYield, setShowYield] = useState(true);
  // Pages belong to the filter they were raised under (as in Transacciones).
  const filterKey = JSON.stringify({ period, showYield });
  const [paging, setPaging] = useState({ filterKey, pages: 1 });
  if (paging.filterKey !== filterKey) setPaging({ filterKey, pages: 1 });
  const limit = (paging.filterKey === filterKey ? paging.pages : 1) * PAGE;

  const activity = useQuery({
    // Under ["investments"], so every investment write refreshes it.
    queryKey: ["investments", investmentId, "activity", filterKey, limit],
    queryFn: () =>
      listInvestmentActivity({
        investmentId,
        ...(period !== null && { period }),
        ...(!showYield && { includeYield: false }),
        limit,
      }),
    placeholderData: (prev, prevQuery) =>
      prevQuery && prevQuery.queryKey[3] === filterKey ? keepPreviousData(prev) : undefined,
  });
  const a = activity.data;
  const signed = (cents: number, negative: boolean) =>
    `${negative ? "−" : "+"}${money(Math.abs(cents), a?.currencyCode)}`;

  return (
    <section className="mb-4 rounded-xl border border-border-muted bg-surface-raised p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="font-medium">{es.investments.activityTitle}</h3>
        {canMove && (
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => onAdd("deposit")}>
              <span className="flex items-center gap-2 text-accent">
                <ArrowDownLeft size={15} /> {es.investments.deposit}
              </span>
            </Button>
            <Button variant="ghost" onClick={() => onAdd("withdrawal")}>
              <span className="flex items-center gap-2 text-danger">
                <ArrowUpRight size={15} /> {es.investments.withdrawal}
              </span>
            </Button>
          </div>
        )}
      </div>

      {a?.observed && (
        <p className="mb-3 text-xs text-fg-subtle">{es.investments.yieldObservedHint}</p>
      )}

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <PeriodPicker
          value={period ?? { kind: "allTime" }}
          onChange={(p) => setPeriod(p.kind === "allTime" ? null : p)}
          allowAll
        />
        <YieldToggle on={showYield} onChange={setShowYield} />
      </div>

      {a && a.count > 0 && (
        <div className="mb-3 flex flex-wrap items-end justify-between gap-x-4 gap-y-1 rounded-lg bg-surface px-4 py-3">
          {showYield && (
            <div>
              <p className="text-xs uppercase tracking-wide text-fg-subtle">
                {es.investments.totalYield}
              </p>
              <p
                className={`text-2xl font-semibold tabular-nums ${
                  a.yieldCents < 0 ? "text-danger" : "text-accent"
                }`}
              >
                {signed(a.yieldCents, a.yieldCents < 0)}
              </p>
            </div>
          )}
          <div className="text-xs text-fg-subtle sm:text-right">
            <p className="tabular-nums">
              {es.investments.totalDeposited} {money(a.depositCents, a.currencyCode)} ·{" "}
              {es.investments.totalWithdrawn} {money(a.withdrawalCents, a.currencyCode)}
            </p>
            <p>
              {a.count === 1
                ? es.dashboard.movementOne
                : es.dashboard.movementsCount.replace("{n}", String(a.count))}
            </p>
          </div>
        </div>
      )}

      {a && a.rows.length === 0 ? (
        <p className="py-2 text-sm text-fg-subtle">
          {period !== null || !showYield
            ? es.investments.activityEmpty
            : canMove
              ? es.investments.movementsEmpty
              : es.investments.yieldEmpty}
        </p>
      ) : (
        a && (
          <>
            <ul className="divide-y divide-border-muted">
              {a.rows.map((r, i) => {
                const isYield = r.kind === "yield";
                const negative = r.kind === "withdrawal" || r.amountCents < 0;
                const tone = negative ? "text-danger" : "text-accent";
                const Icon = isYield
                  ? TrendingUp
                  : r.kind === "deposit"
                    ? ArrowDownLeft
                    : ArrowUpRight;
                return (
                  <li
                    key={r.movementId ?? `y-${r.occurredAt}-${i}`}
                    className="group flex items-center gap-3 py-2 text-sm"
                  >
                    <span
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-surface-overlay ${tone}`}
                    >
                      <Icon size={13} />
                    </span>
                    <span className="text-fg">
                      {isYield
                        ? es.transactions.yieldRow
                        : r.kind === "deposit"
                          ? es.investments.depositNoun
                          : es.investments.withdrawalNoun}
                    </span>
                    <span className="text-fg-subtle">{r.occurredAt}</span>
                    <span className={`ml-auto tabular-nums ${tone}`}>
                      {signed(r.amountCents, negative)}
                    </span>
                    {/* Fixed slot, so amounts line up with or without buttons. */}
                    <div className="flex w-14 shrink-0 justify-end gap-1">
                      {r.movementId !== null && (
                        <>
                          <button
                            onClick={() => onEdit(r.movementId!)}
                            aria-label={es.common.edit}
                            className="touch-action-reveal rounded-md p-1 text-fg-subtle transition-all hover:bg-surface-overlay hover:text-fg"
                          >
                            <Pencil size={14} />
                          </button>
                          <button
                            onClick={() => onDelete(r.movementId!)}
                            aria-label={es.common.delete}
                            className="touch-action-reveal rounded-md p-1 text-fg-subtle transition-all hover:bg-danger/10 hover:text-danger"
                          >
                            <Trash2 size={14} />
                          </button>
                        </>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
            {(a.hasMore || activity.isPlaceholderData) && (
              <div className="mt-2 flex justify-center">
                <Button
                  variant="ghost"
                  disabled={activity.isPlaceholderData}
                  onClick={() =>
                    setPaging({ filterKey, pages: limit / PAGE + 1 })
                  }
                >
                  {activity.isPlaceholderData ? es.common.loading : es.transactions.showMore}
                </Button>
              </div>
            )}
          </>
        )
      )}
    </section>
  );
}
