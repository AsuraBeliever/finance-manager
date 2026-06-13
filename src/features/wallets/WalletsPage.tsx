import { useQuery } from "@tanstack/react-query";
import { Plus, Wallet as WalletIcon } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { WalletCard } from "../../components/WalletCard";
import { listWallets } from "../../lib/api";
import { es } from "../../i18n/es";
import { WalletFormModal } from "./WalletFormModal";

export function WalletsPage() {
  const [showArchived, setShowArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const wallets = useQuery({
    queryKey: ["wallets", { showArchived }],
    queryFn: () => listWallets(showArchived),
  });

  const visible = wallets.data ?? [];

  return (
    <>
      <PageHeader
        title={es.wallets.title}
        actions={
          <div className="flex items-center gap-4">
            <label className="flex cursor-pointer items-center gap-2 text-sm text-fg-muted">
              <input
                type="checkbox"
                checked={showArchived}
                onChange={(e) => setShowArchived(e.target.checked)}
                className="accent-accent"
              />
              {es.wallets.showArchived}
            </label>
            <Button onClick={() => setFormOpen(true)}>
              <span className="flex items-center gap-2">
                <Plus size={16} /> {es.wallets.newWallet}
              </span>
            </Button>
          </div>
        }
      />

      {wallets.isError && <p className="text-sm text-danger">{String(wallets.error)}</p>}

      {wallets.isSuccess && visible.length === 0 && (
        <EmptyState
          icon={WalletIcon}
          title={es.wallets.emptyTitle}
          description={es.wallets.emptyDescription}
        />
      )}

      <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-5">
        {visible.map((w) => (
          <WalletCard key={w.id} wallet={w} />
        ))}
      </div>

      <WalletFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
