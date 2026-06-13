import { useQuery } from "@tanstack/react-query";
import { Plus, Wallet as WalletIcon } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { listWallets } from "../../lib/api";
import { formatCents } from "../../lib/money";
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

      <div className="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-4">
        {visible.map((w) => (
          <Link
            key={w.id}
            to={`/carteras/${w.id}`}
            className="group rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:border-accent/50"
          >
            <div className="mb-3 flex items-center gap-2">
              <span
                className="h-3 w-3 rounded-full ring-2 ring-white/5"
                style={{ backgroundColor: w.color ?? "#a8a29e" }}
              />
              <span className="truncate font-medium text-fg">{w.name}</span>
              {w.isArchived && (
                <span className="ml-auto rounded-full bg-surface-overlay px-2 py-0.5 text-xs text-fg-subtle">
                  {es.wallets.archived}
                </span>
              )}
            </div>
            <p className="font-display text-2xl font-medium tabular-nums text-fg">
              {formatCents(w.balanceCents, w.currencyCode)}
            </p>
            <p className="mt-1 text-xs text-fg-subtle">
              {w.categoryName} · {w.currencyCode}
            </p>
          </Link>
        ))}
      </div>

      <WalletFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
