import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { PrivacyToggle } from "../../components/PrivacyToggle";
import { listTransactions, listWallets, type TxFilter } from "../../lib/api";
import type { Transaction } from "../../lib/types";
import { es } from "../../i18n/es";
import { TransactionFilters, type FilterKind } from "./TransactionFilters";
import { TransactionFormModal } from "./TransactionFormModal";
import { TransactionList } from "./TransactionList";
import { OutboxPanel } from "./OutboxPanel";

// The active filter survives tab switches and reloads.
const FILTER_KEY = "finanzas.txFilter";

interface PersistedFilter {
  walletId: number | "";
  kind: FilterKind;
  categoryId: number | "";
}

function loadFilter(): PersistedFilter {
  try {
    const raw = localStorage.getItem(FILTER_KEY);
    if (raw) return JSON.parse(raw) as PersistedFilter;
  } catch {
    // ignore corrupt/unavailable storage
  }
  return { walletId: "", kind: "", categoryId: "" };
}

export function TransactionsPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [walletId, setWalletId] = useState<number | "">(() => loadFilter().walletId);
  const [kind, setKind] = useState<FilterKind>(() => loadFilter().kind);
  const [categoryId, setCategoryId] = useState<number | "">(() => loadFilter().categoryId);

  useEffect(() => {
    localStorage.setItem(FILTER_KEY, JSON.stringify({ walletId, kind, categoryId }));
  }, [walletId, kind, categoryId]);

  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const filter: TxFilter = {
    ...(walletId !== "" && { walletId }),
    ...(kind !== "" && { kind }),
    ...(categoryId !== "" && { categoryId }),
  };
  const transactions = useQuery({
    queryKey: ["transactions", filter],
    queryFn: () => listTransactions(filter),
  });
  const filtered = walletId !== "" || kind !== "" || categoryId !== "";

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
        walletId={walletId}
        onWalletChange={setWalletId}
      />

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
          <TransactionList
            transactions={transactions.data}
            currencyByWallet={currencyByWallet}
            onEdit={setEditing}
          />
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
