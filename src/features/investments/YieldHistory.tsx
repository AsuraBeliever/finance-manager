import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "../../components/Button";
import { getInvestmentYield } from "../../lib/api";
import { formatMonthYear } from "../../lib/date";
import { useMoney } from "../../lib/hideBalance";
import { es } from "../../i18n/es";

/** How many days the list shows at first, and how many each «Ver más» adds. */
const DAYS_PAGE = 30;

/** What an investment earned per month and per day (FIN-9). Computed and
 *  stored by the worker; this only lays it out. */
export function YieldHistory({ investmentId }: { investmentId: number }) {
  const money = useMoney();
  const [limit, setLimit] = useState(DAYS_PAGE);
  const history = useQuery({
    // Under ["investments"], so every investment write refreshes it.
    queryKey: ["investments", investmentId, "yield", limit],
    queryFn: () => getInvestmentYield(investmentId, limit),
    // Keep the days on screen while the longer list loads.
    placeholderData: keepPreviousData,
  });
  const h = history.data;
  const signed = (cents: number) =>
    `${cents < 0 ? "−" : "+"}${money(Math.abs(cents), h?.currencyCode)}`;
  const tone = (cents: number) => (cents < 0 ? "text-danger" : "text-accent");

  return (
    <section className="mb-4 rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="font-medium">{es.investments.yieldHistory}</h3>
      <p className="mt-1 mb-3 text-xs text-fg-subtle">
        {h?.observed ? es.investments.yieldObservedHint : es.investments.yieldHistoryHint}
      </p>
      {h && h.days.length === 0 ? (
        <p className="py-2 text-sm text-fg-subtle">{es.investments.yieldEmpty}</p>
      ) : (
        h && (
          <>
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-fg-subtle">
              {es.investments.yieldByMonth}
            </p>
            <ul className="mb-4 divide-y divide-border-muted">
              {h.months.map((m) => (
                <li key={m.month} className="flex items-center justify-between py-2 text-sm">
                  <span className="text-fg-muted first-letter:uppercase">
                    {formatMonthYear(m.month)}
                  </span>
                  <span className={`tabular-nums ${tone(m.gainCents)}`}>{signed(m.gainCents)}</span>
                </li>
              ))}
            </ul>
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-fg-subtle">
              {es.investments.yieldByDay}
            </p>
            <ul className="divide-y divide-border-muted">
              {h.days.map((d) => (
                <li key={d.day} className="flex items-center justify-between py-2 text-sm">
                  <span className="text-fg-muted">{d.day}</span>
                  <span className={`tabular-nums ${tone(d.gainCents)}`}>{signed(d.gainCents)}</span>
                </li>
              ))}
            </ul>
            {h.hasMore && (
              <div className="mt-2 flex justify-center">
                <Button
                  variant="ghost"
                  disabled={history.isPlaceholderData}
                  onClick={() => setLimit(limit + DAYS_PAGE)}
                >
                  {history.isPlaceholderData ? es.common.loading : es.investments.yieldShowMore}
                </Button>
              </div>
            )}
          </>
        )
      )}
    </section>
  );
}
