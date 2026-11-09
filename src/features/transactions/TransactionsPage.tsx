import { useQuery } from "@tanstack/react-query";
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

  useEffect(() => {
    localStorage.setItem(FILTER_KEY, JSON.stringify({ walletIds, kind, categoryId, period }));
  }, [walletIds, kind, categoryId, period]);

  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const filter: TxFilter = {
    // Sorted so the same picks in another order share a cache entry.
    ...(walletIds.length > 0 && { walletIds: [...walletIds].sort((a, b) => a - b) }),
    ...(kind !== "" && { kind }),
    ...(categoryId !== "" && { categoryId }),
    ...(period !== null && { period }),
  };
  const transactions = useQuery({
    queryKey: ["transactions", filter],
    queryFn: () => listTransactions(filter),
  });
  const filtered = walletIds.length > 0 || kind !== "" || categoryId !== "" || period !== null;

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
            {transactions.data.length >= TX_LIST_LIMIT && (
              <p className="mt-3 text-center text-xs text-fg-subtle">
                {es.transactions.listCapped.replace("{n}", String(TX_LIST_LIMIT))}
              </p>
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
