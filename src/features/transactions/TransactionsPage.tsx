import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { inputClass } from "../../components/Field";
import { listTransactions, listWallets, type TxFilter } from "../../lib/api";
import type { Transaction, TransactionKind } from "../../lib/types";
import { es } from "../../i18n/es";
import { TransactionFormModal } from "./TransactionFormModal";
import { TransactionList } from "./TransactionList";
import { OutboxPanel } from "./OutboxPanel";

export function TransactionsPage() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [walletId, setWalletId] = useState<number | "">("");
  const [kind, setKind] = useState<TransactionKind | "">("");

  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  const filter: TxFilter = {
    ...(walletId !== "" && { walletId }),
    ...(kind !== "" && { kind }),
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

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
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
        <div className="w-full sm:w-48">
          <select
            className={inputClass}
            value={kind}
            onChange={(e) => setKind(e.target.value as TransactionKind | "")}
          >
            <option value="">{es.transactions.allKinds}</option>
            <option value="income">{es.transactions.income}</option>
            <option value="expense">{es.transactions.expense}</option>
            <option value="transfer_in">{es.transactions.transfer} (+)</option>
            <option value="transfer_out">{es.transactions.transfer} (−)</option>
          </select>
        </div>
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
