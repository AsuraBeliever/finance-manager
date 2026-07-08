import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { deleteSavingsGoal, listSavingsGoals, listWallets, useSavingsGoal } from "../../lib/api";
import { formatCents } from "../../lib/money";
import type { SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";
import { WalletFormModal } from "../wallets/WalletFormModal";
import { ContributeModal } from "./ContributeModal";
import { GoalCard } from "./GoalCard";
import { GoalFormModal } from "./GoalFormModal";

/** Goals whose apartado lives in this wallet, shown on the wallet detail page
 *  with the same card + actions as the goals page. Renders nothing when the
 *  wallet has no linked goals. */
export function WalletGoalsSection({ walletId }: { walletId: number }) {
  const qc = useQueryClient();
  const goals = useQuery({ queryKey: ["savingsGoals"], queryFn: () => listSavingsGoals() });
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const [formGoal, setFormGoal] = useState<SavingsGoal | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [contribFor, setContribFor] = useState<SavingsGoal | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [useGoal, setUseGoal] = useState<SavingsGoal | null>(null);
  const [convertGoal, setConvertGoal] = useState<SavingsGoal | null>(null);

  const invalidate = () => {
    // Apartado moves/uses touch wallet balances + the ledger, so refresh both.
    for (const key of ["savingsGoals", "wallets", "transactions", "dashboard"]) {
      qc.invalidateQueries({ queryKey: [key] });
    }
  };
  const remove = useMutation({ mutationFn: deleteSavingsGoal, onSuccess: invalidate });
  const useMut = useMutation({
    mutationFn: (id: number) => useSavingsGoal(id),
    onSuccess: invalidate,
    onSettled: () => setUseGoal(null),
  });
  const walletName = (id: number | null) =>
    id == null ? null : (wallets.data?.find((w) => w.id === id)?.name ?? null);

  const linked = (goals.data ?? []).filter((g) => g.linkedWalletId === walletId);
  if (linked.length === 0) return null;

  return (
    <div className="mb-6">
      <h3 className="mb-3 font-medium">{es.goals.walletSectionTitle}</h3>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] items-start gap-4">
        {linked.map((g) => (
          <GoalCard
            key={g.id}
            goal={g}
            walletName={walletName(g.linkedWalletId)}
            onEdit={() => {
              setFormGoal(g);
              setFormOpen(true);
            }}
            onDelete={() => setDeleteId(g.id)}
            onContribute={() => setContribFor(g)}
            onUse={() => setUseGoal(g)}
            onConvert={() => setConvertGoal(g)}
          />
        ))}
      </div>

      <GoalFormModal
        open={formOpen}
        goal={formGoal}
        onClose={() => setFormOpen(false)}
        onSaved={invalidate}
      />
      <ContributeModal
        goal={contribFor}
        onClose={() => setContribFor(null)}
        onSaved={invalidate}
      />
      <ConfirmDialog
        open={deleteId !== null}
        title={es.common.delete}
        message={es.goals.deleteConfirm}
        confirmLabel={es.common.delete}
        onConfirm={() => {
          if (deleteId !== null) remove.mutate(deleteId);
          setDeleteId(null);
        }}
        onClose={() => setDeleteId(null)}
      />
      <ConfirmDialog
        open={useGoal !== null}
        title={es.goals.buy}
        message={
          useGoal?.linkedWalletId
            ? es.goals.useConfirmApartado
                .replace("{amount}", formatCents(useGoal.savedCents, useGoal.currencyCode))
                .replace("{wallet}", walletName(useGoal.linkedWalletId) ?? "")
            : es.goals.useConfirmTrack
        }
        confirmLabel={es.goals.buy}
        onConfirm={() => {
          if (useGoal) useMut.mutate(useGoal.id);
        }}
        onClose={() => setUseGoal(null)}
      />
      {convertGoal && (
        <WalletFormModal
          open
          convert={{
            goalId: convertGoal.id,
            name: convertGoal.name,
            color: convertGoal.color,
            currencyCode: convertGoal.currencyCode,
            savedCents: convertGoal.savedCents,
            sourceCategoryId:
              wallets.data?.find((w) => w.id === convertGoal.linkedWalletId)?.categoryId ?? null,
            sourceWalletId: convertGoal.linkedWalletId,
          }}
          onClose={() => setConvertGoal(null)}
        />
      )}
    </div>
  );
}
