import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { PrivacyToggle } from "../../components/PrivacyToggle";
import { listTransactions, listWallets, TX_LIST_LIMIT, type TxFilter } from "../../lib/api";
import type { Period, Transaction } from "../../lib/types";
import { es } from "../../i18n/es";
import { TransactionFilters, type FilterKind } from "./TransactionFilters";
import { TransactionFormModal } from "./TransactionFormModal";
import { TransactionList } from "./TransactionList";
import { TransactionTotal } from "./TransactionTotal";
import { OutboxPanel } from "./OutboxPanel";

// The active filter survives tab switches and reloads.
const FILTER_KEY = "finanzas.txFilter";

interface PersistedFilter {
  walletId: number | "";
  kind: FilterKind;
  categoryId: number | "";
  /** null = every date. Absent in filters saved before periods existed. */
  period?: Period | null;
}

function loadFilter(): PersistedFilter {
  try {
    const raw = localStorage.getItem(FILTER_KEY);
    if (raw) return JSON.parse(raw) as PersistedFilter;
  } catch {
    // ignore corrupt/unavailable storage
  }
  return { walletId: "", kind: "", categoryId: "", period: null };
}

export function TransactionsPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [walletId, setWalletId] = useState<number | "">(() => loadFilter().walletId);
  const [kind, setKind] = useState<FilterKind>(() => loadFilter().kind);
  const [categoryId, setCategoryId] = useState<number | "">(() => loadFilter().categoryId);
  const [period, setPeriod] = useState<Period | null>(() => loadFilter().period ?? null);

  useEffect(() => {
    localStorage.setItem(FILTER_KEY, JSON.stringify({ walletId, kind, categoryId, period }));
  }, [walletId, kind, categoryId, period]);

  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const filter: TxFilter = {
    ...(walletId !== "" && { walletId }),
    ...(kind !== "" && { kind }),
    ...(categoryId !== "" && { categoryId }),
    ...(period !== null && { period }),
  };
  // "Mostrar más" grows the request by one page at a time and refetches from
  // the top, rather than appending offset pages: a movement captured while
  // browsing then can't shift a row into a gap or show it twice, and both
  // legs of a transfer always arrive together for the list to fold. The page
  // count belongs to the filter it was raised under, so a new filter starts
  // back at the first page.
  const filterKey = JSON.stringify(filter);
  const [paging, setPaging] = useState({ filterKey, pages: 1 });
  // Any filter change drops back to the first page — including returning to
  // a filter that had been paged before. Adjusted during render (React's
  // "storing information from previous renders"), so no frame shows the old
  // count under the new filter.
  if (paging.filterKey !== filterKey) setPaging({ filterKey, pages: 1 });
  const pages = paging.filterKey === filterKey ? paging.pages : 1;
  const limit = pages * TX_LIST_LIMIT;
  const transactions = useQuery({
    // "list" keeps this entry apart from the wallet page's ["transactions", filter].
    queryKey: ["transactions", "list", filter, limit],
    queryFn: () => listTransactions({ ...filter, limit }),
    // Keep the rows on screen while the next page loads — but only for the
    // same filter; a different filter shows nothing until its own rows arrive.
    placeholderData: (prev, prevQuery) =>
      prevQuery && JSON.stringify(prevQuery.queryKey[2]) === filterKey
        ? keepPreviousData(prev)
        : undefined,
  });
  const loadingMore = transactions.isPlaceholderData;
  const filtered = walletId !== "" || kind !== "" || categoryId !== "" || period !== null;

  const currencyByWallet = useMemo(
    () => new Map((wallets.data ?? []).map((w) => [w.id, w.currencyCode])),
    [wallets.data],
  );

  return (
    <>
      <PageHeader
        title={es.transactions.title}
        actions={
          <div className="flex items-center gap-4">
            <PrivacyToggle />
            <Button onClick={() => setFormOpen(true)}>
              <span className="flex items-center gap-2">
                <Plus size={16} /> {es.transactions.newTransaction}
              </span>
            </Button>
          </div>
        }
      />

      <OutboxPanel />

      <TransactionFilters
        kind={kind}
        categoryId={categoryId}
        onChange={(next) => {
          setKind(next.kind);
          setCategoryId(next.categoryId);
        }}
        period={period}
        onPeriodChange={setPeriod}
        walletId={walletId}
        onWalletChange={setWalletId}
      />

      <TransactionTotal filter={filter} />

      {transactions.isError && (
        <p className="text-sm text-danger">{String(transactions.error)}</p>
      )}

      {transactions.isSuccess && transactions.data.length === 0 ? (
        <EmptyState
          icon={ArrowLeftRight}
          title={filtered ? es.transactions.noMatchTitle : es.transactions.emptyTitle}
          description={
            filtered ? es.transactions.noMatchDescription : es.transactions.emptyDescription
          }
        />
      ) : (
        transactions.data && (
          <>
            <TransactionList
              transactions={transactions.data}
              currencyByWallet={currencyByWallet}
              onEdit={setEditing}
            />
            {/* A full page means there are probably more. Counted on the raw
                rows: the server capped those, and a transfer costs two. */}
            {transactions.data.length >= limit && (
              <div className="mt-3 flex flex-col items-center gap-2">
                <p className="text-center text-xs text-fg-subtle">
                  {es.transactions.listCapped.replace("{n}", String(transactions.data.length))}
                </p>
                <Button
                  variant="ghost"
                  disabled={loadingMore}
                  onClick={() => setPaging({ filterKey, pages: pages + 1 })}
                >
                  {loadingMore ? es.common.loading : es.transactions.showMore}
                </Button>
              </div>
            )}
          </>
        )
      )}

      <TransactionFormModal
        open={formOpen || editing !== null}
        transaction={editing ?? undefined}
        onClose={() => {
          setFormOpen(false);
          setEditing(null);
        }}
      />
    </>
  );
}
