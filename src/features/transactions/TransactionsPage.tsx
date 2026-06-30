import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { inputClass } from "../../components/Field";
import {
  listFilterCategories,
  listTransactions,
  listWallets,
  type TxFilter,
} from "../../lib/api";
import { seedName } from "../../i18n/seed";
import type { Transaction, TransactionKind } from "../../lib/types";
import { es } from "../../i18n/es";
import { TransactionFormModal } from "./TransactionFormModal";
import { TransactionList } from "./TransactionList";
import { OutboxPanel } from "./OutboxPanel";

type FilterKind = TransactionKind | "transfer" | "";

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
  const categories = useQuery({
    queryKey: ["filterCategories"],
    queryFn: listFilterCategories,
  });

  const filter: TxFilter = {
    ...(walletId !== "" && { walletId }),
    ...(kind !== "" && { kind }),
    ...(categoryId !== "" && { categoryId }),
  };
  const transactions = useQuery({
    queryKey: ["transactions", filter],
    queryFn: () => listTransactions(filter),
  });

  const currencyByWallet = useMemo(
    () => new Map((wallets.data ?? []).map((w) => [w.id, w.currencyCode])),
    [wallets.data],
  );

  return (
    <>
      <PageHeader
        title={es.transactions.title}
        actions={
          <Button onClick={() => setFormOpen(true)}>
            <span className="flex items-center gap-2">
              <Plus size={16} /> {es.transactions.newTransaction}
            </span>
          </Button>
        }
      />

      <OutboxPanel />

      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <div className="w-full sm:w-56">
          <select
            className={inputClass}
            value={walletId}
            onChange={(e) =>
              setWalletId(e.target.value === "" ? "" : Number(e.target.value))
            }
          >
            <option value="">{es.transactions.allWallets}</option>
            {wallets.data?.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </select>
        </div>
        {/* Type as segmented buttons; picking a kind also resets the category. */}
        <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
          {(
            [
              ["", es.transactions.typeAll],
              ["income", es.transactions.income],
              ["expense", es.transactions.expense],
              ["transfer", es.transactions.transfer],
            ] as const
          ).map(([val, label]) => (
            <button
              key={val || "all"}
              type="button"
              onClick={() => {
                setKind(val);
                setCategoryId("");
              }}
              className={`flex-1 whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition-colors sm:flex-none ${
                kind === val
                  ? "bg-surface-raised text-fg shadow-sm"
                  : "text-fg-subtle hover:text-fg"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {/* Category only applies to income/expense, scoped to the chosen kind. */}
        {(kind === "income" || kind === "expense") && (
          <div className="w-full sm:w-56">
            <select
              className={inputClass}
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value === "" ? "" : Number(e.target.value))}
            >
              <option value="">{es.transactions.allCategories}</option>
              {categories.data
                ?.filter((c) => c.kind === kind)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {seedName(c.name, c.isSystem)}
                  </option>
                ))}
            </select>
          </div>
        )}
      </div>

      {transactions.isError && (
        <p className="text-sm text-danger">{String(transactions.error)}</p>
      )}

      {transactions.isSuccess && transactions.data.length === 0 ? (
        <EmptyState
          icon={ArrowLeftRight}
          title={es.transactions.emptyTitle}
          description={es.transactions.emptyDescription}
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
