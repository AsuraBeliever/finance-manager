import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ArchiveRestore, ArrowLeftRight, Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { WalletCard } from "../../components/WalletCard";
import {
  archiveWallet,
  deleteWallet,
  getWallet,
  listTransactions,
  listWallets,
} from "../../lib/api";
import type { Transaction } from "../../lib/types";
import { formatCents } from "../../lib/money";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";
import { TransactionFormModal } from "../transactions/TransactionFormModal";
import { TransactionList } from "../transactions/TransactionList";
import { CreditCardPanel } from "./CreditCardPanel";
import { WalletFormModal } from "./WalletFormModal";

export function WalletDetailPage() {
  const { id } = useParams();
  const walletId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);
  const [txFormOpen, setTxFormOpen] = useState(false);
  const [editingTx, setEditingTx] = useState<Transaction | null>(null);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [addApartadoOpen, setAddApartadoOpen] = useState(false);

  const wallet = useQuery({
    queryKey: ["wallets", walletId],
    queryFn: () => getWallet(walletId),
    enabled: Number.isFinite(walletId),
  });

  // This wallet's apartados (pockets nested under it).
  const allWallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const apartados = (allWallets.data ?? []).filter((x) => x.parentWalletId === walletId);

  const transactions = useQuery({
    queryKey: ["transactions", { walletId }],
    queryFn: () => listTransactions({ walletId }),
    enabled: Number.isFinite(walletId),
  });

  const currencyByWallet = useMemo(
    () =>
      wallet.data
        ? new Map([[wallet.data.id, wallet.data.currencyCode]])
        : undefined,
    [wallet.data],
  );

  const wd = wallet.data;
  // Stable parent descriptor for the "add pocket" form (category + currency
  // inherited from this wallet).
  const apartadoParent = useMemo(
    () => (wd ? { id: wd.id, categoryId: wd.categoryId, currencyCode: wd.currencyCode } : undefined),
    [wd?.id, wd?.categoryId, wd?.currencyCode],
  );

  const archive = useMutation({
    mutationFn: (archived: boolean) => archiveWallet(walletId, archived),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      navigate("/carteras");
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteWallet(walletId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      navigate("/carteras");
    },
  });

  if (wallet.isPending) return <p className="text-sm text-fg-subtle">{es.common.loading}</p>;
  if (wallet.isError) return <p className="text-sm text-danger">{String(wallet.error)}</p>;

  const w = wallet.data;

  return (
    <>
      <PageHeader
        title={w.name}
        actions={
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => setEditOpen(true)}>
              <span className="flex items-center gap-2">
                <Pencil size={15} /> {es.common.edit}
              </span>
            </Button>
            <Button
              variant="ghost"
              onClick={() => archive.mutate(!w.isArchived)}
              disabled={archive.isPending}
            >
              <span className="flex items-center gap-2">
                {w.isArchived ? <ArchiveRestore size={15} /> : <Archive size={15} />}
                {w.isArchived ? es.wallets.unarchive : es.wallets.archive}
              </span>
            </Button>
            <Button variant="danger" onClick={() => setConfirmDeleteOpen(true)}>
              <span className="flex items-center gap-2">
                <Trash2 size={15} /> {es.common.delete}
              </span>
            </Button>
          </div>
        }
      />

      <div className="mb-6 rounded-xl border border-border-muted bg-surface-raised p-5">
        <div className="mb-1 flex items-center gap-2">
          <span
            className="h-3 w-3 rounded-full"
            style={{ backgroundColor: w.color ?? "#a8a29e" }}
          />
          <span className="text-sm text-fg-muted">
            {seedName(w.categoryName)} · {w.currencyCode}
          </span>
        </div>
        <p className="text-3xl font-semibold tabular-nums">
          {formatCents(w.balanceCents, w.currencyCode)}
        </p>
        {/* On a credit card the initial balance is registered debt, so
            "Saldo inicial" would be wrong; show it as debt (or nothing). */}
        {w.creditCutDay != null ? (
          w.initialBalanceCents < 0 && (
            <p className="mt-1 text-xs text-fg-subtle">
              {es.credit.initialDebtLine}:{" "}
              {formatCents(-w.initialBalanceCents, w.currencyCode)}
            </p>
          )
        ) : (
          <p className="mt-1 text-xs text-fg-subtle">
            {es.wallets.initialBalance}:{" "}
            {formatCents(w.initialBalanceCents, w.currencyCode)}
          </p>
        )}
        {w.notes && <p className="mt-2 text-sm text-fg-muted">{w.notes}</p>}
      </div>

      {/* Statement panel + MSI — only when this wallet is a credit card. */}
      {w.creditCutDay != null && <CreditCardPanel wallet={w} />}

      {/* Apartados (pockets) of this wallet — only for top-level wallets, since
          apartados stay one level deep. */}
      {w.parentWalletId == null && (
        <div className="mb-6">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-medium">{es.wallets.apartadosLabel}</h3>
            <Button variant="ghost" onClick={() => setAddApartadoOpen(true)}>
              <span className="flex items-center gap-2">
                <Plus size={15} /> {es.wallets.addApartado}
              </span>
            </Button>
          </div>
          {apartados.length > 0 && (
            <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] items-start gap-4">
              {apartados.map((a) => (
                <WalletCard key={a.id} wallet={a} />
              ))}
            </div>
          )}
        </div>
      )}

      <div className="mb-3 flex items-center justify-between">
        <h3 className="font-medium">{es.transactions.title}</h3>
        <Button variant="ghost" onClick={() => setTxFormOpen(true)}>
          <span className="flex items-center gap-2">
            <Plus size={15} /> {es.transactions.newTransaction}
          </span>
        </Button>
      </div>

      {transactions.data && transactions.data.length === 0 ? (
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
            showWallet={false}
            onEdit={setEditingTx}
          />
        )
      )}

      <WalletFormModal open={editOpen} onClose={() => setEditOpen(false)} wallet={w} />
      <WalletFormModal
        open={addApartadoOpen}
        onClose={() => setAddApartadoOpen(false)}
        defaultParent={apartadoParent}
      />
      <TransactionFormModal
        open={txFormOpen || editingTx !== null}
        transaction={editingTx ?? undefined}
        onClose={() => {
          setTxFormOpen(false);
          setEditingTx(null);
        }}
        defaultWalletId={walletId}
      />
      <ConfirmDialog
        open={confirmDeleteOpen}
        title={es.wallets.deleteConfirmTitle}
        message={es.wallets.deleteConfirmMessage}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirmDeleteOpen(false)}
      />
    </>
  );
}
