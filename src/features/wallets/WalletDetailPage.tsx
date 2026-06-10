import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ArchiveRestore, Pencil } from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button } from "../../components/Button";
import { PageHeader } from "../../components/PageHeader";
import { archiveWallet, getWallet } from "../../lib/api";
import { formatCents } from "../../lib/money";
import { es } from "../../i18n/es";
import { WalletFormModal } from "./WalletFormModal";

export function WalletDetailPage() {
  const { id } = useParams();
  const walletId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);

  const wallet = useQuery({
    queryKey: ["wallets", walletId],
    queryFn: () => getWallet(walletId),
    enabled: Number.isFinite(walletId),
  });

  const archive = useMutation({
    mutationFn: (archived: boolean) => archiveWallet(walletId, archived),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wallets"] });
      navigate("/carteras");
    },
  });

  if (wallet.isPending) return <p className="text-sm text-zinc-500">{es.common.loading}</p>;
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
          </div>
        }
      />

      <div className="mb-6 rounded-xl border border-border-muted bg-surface-raised p-5">
        <div className="mb-1 flex items-center gap-2">
          <span
            className="h-3 w-3 rounded-full"
            style={{ backgroundColor: w.color ?? "#94a3b8" }}
          />
          <span className="text-sm text-zinc-400">
            {w.categoryName} · {w.currencyCode}
          </span>
        </div>
        <p className="text-3xl font-semibold tabular-nums">
          {formatCents(w.balanceCents, w.currencyCode)}
        </p>
        <p className="mt-1 text-xs text-zinc-500">
          {es.wallets.initialBalance}:{" "}
          {formatCents(w.initialBalanceCents, w.currencyCode)}
        </p>
        {w.notes && <p className="mt-2 text-sm text-zinc-400">{w.notes}</p>}
      </div>

      <WalletFormModal open={editOpen} onClose={() => setEditOpen(false)} wallet={w} />
    </>
  );
}
