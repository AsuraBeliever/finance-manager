import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { inputClass } from "../../components/Field";
import { listTransactions, listWallets, type TxFilter } from "../../lib/api";
import type { TransactionKind } from "../../lib/types";
import { es } from "../../i18n/es";
import { TransactionFormModal } from "./TransactionFormModal";
import { TransactionList } from "./TransactionList";

export function TransactionsPage() {
  const [formOpen, setFormOpen] = useState(false);
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

      <div className="mb-4 flex gap-3">
        <select
          className={`${inputClass} w-56`}
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
        <select
          className={`${inputClass} w-48`}
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
          />
        )
      )}

      <TransactionFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
