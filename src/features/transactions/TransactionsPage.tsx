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
  /** Empty = every wallet. */
  walletIds?: number[];
  /** The single wallet saved before several could be picked. */
  walletId?: number | "";
  kind: FilterKind;
  categoryId: number | "";
  /** null = every date. Absent in filters saved before periods existed. */
  period?: Period | null;
  /** «Rendimientos» toggle; absent (saved before it existed) = shown. */
  showYield?: boolean;
}

function loadFilter(): PersistedFilter {
  try {
    const raw = localStorage.getItem(FILTER_KEY);
    if (raw) return JSON.parse(raw) as PersistedFilter;
  } catch {
    // ignore corrupt/unavailable storage
  }
  return { walletIds: [], kind: "", categoryId: "", period: null };
}

/** The saved wallets, reading a filter saved with the old single wallet too. */
function loadWalletIds(): number[] {
  const f = loadFilter();
  if (f.walletIds) return f.walletIds;
  return typeof f.walletId === "number" ? [f.walletId] : [];
}

export function TransactionsPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [walletIds, setWalletIds] = useState<number[]>(loadWalletIds);
  const [kind, setKind] = useState<FilterKind>(() => loadFilter().kind);
  const [categoryId, setCategoryId] = useState<number | "">(() => loadFilter().categoryId);
  const [period, setPeriod] = useState<Period | null>(() => loadFilter().period ?? null);
  const [showYield, setShowYield] = useState<boolean>(() => loadFilter().showYield ?? true);

  useEffect(() => {
    localStorage.setItem(
      FILTER_KEY,
      JSON.stringify({ walletIds, kind, categoryId, period, showYield }),
    );
  }, [walletIds, kind, categoryId, period, showYield]);

  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const filter: TxFilter = {
    // Sorted so the same picks in another order share a cache entry.
    ...(walletIds.length > 0 && { walletIds: [...walletIds].sort((a, b) => a - b) }),
    ...(kind !== "" && { kind }),
    ...(categoryId !== "" && { categoryId }),
    ...(period !== null && { period }),
    ...(!showYield && { includeYield: false }),
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
  const filtered =
    walletIds.length > 0 || kind !== "" || categoryId !== "" || period !== null || !showYield;

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
        walletIds={walletIds}
        onWalletsChange={setWalletIds}
        showYield={showYield}
        onShowYieldChange={setShowYield}
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
            {/* While the next page loads the old, shorter list is still on
                screen, so keep the footer up and show it loading. */}
            {(loadingMore || transactions.data.length >= limit) && (
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
