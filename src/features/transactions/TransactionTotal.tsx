import { useQuery } from "@tanstack/react-query";
import { sumTransactions, type TxFilter } from "../../lib/api";
import { useMoney } from "../../lib/hideBalance";
import { es } from "../../i18n/es";

/** What the filtered transactions add up to. Only meaningful for income or
 *  expense — transfers just move money between the user's own wallets — so it
 *  renders nothing for any other filter, and the worker never gets asked.
 *  Every figure comes from Rust; this only formats.
 *
 *  The query key sits under ["transactions", …] so the invalidations that
 *  follow a new, edited or deleted transaction refresh the total too. */
export function TransactionTotal({ filter }: { filter: TxFilter }) {
  const money = useMoney();
  const enabled = filter.kind === "income" || filter.kind === "expense";

  const totals = useQuery({
    queryKey: ["transactions", "totals", filter],
    queryFn: () => sumTransactions(filter),
    enabled,
  });

  if (!enabled || !totals.data) return null;
  const t = totals.data;
  if (t.count === 0) return null;

  // One currency reads in its own money; a mix reads in MXN, with each
  // currency's real figure spelled out underneath so nothing looks invented.
  const single = t.byCurrency.length === 1 ? t.byCurrency[0] : undefined;
  const countLabel =
    t.count === 1
      ? es.dashboard.movementOne
      : es.dashboard.movementsCount.replace("{n}", String(t.count));

  return (
    <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 rounded-xl border border-border-muted bg-surface-raised px-4 py-3">
      <div>
        <p className="text-xs uppercase tracking-wide text-fg-subtle">
          {filter.kind === "income" ? es.transactions.totalIncome : es.transactions.totalExpense}
        </p>
        <p
          className={`text-2xl font-semibold tabular-nums ${
            filter.kind === "income" ? "text-accent" : "text-danger"
          }`}
        >
          {single
            ? money(single.cents, single.currencyCode)
            : money(t.totalMxnCents, "MXN")}
        </p>
      </div>
      <div className="text-right text-xs text-fg-subtle">
        <p>{countLabel}</p>
        {!single && (
          <p className="tabular-nums">
            {es.transactions.totalConverted} ·{" "}
            {t.byCurrency
              .map((c) => `${money(c.cents, c.currencyCode)} ${c.currencyCode}`)
              .join(" · ")}
          </p>
        )}
      </div>
    </div>
  );
}
