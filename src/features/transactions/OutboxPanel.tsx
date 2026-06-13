import { useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { CloudOff, RotateCw, Trash2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { listWallets } from "../../lib/api";
import { formatCents } from "../../lib/money";
import {
  discard,
  flush,
  getItems,
  retryItem,
  subscribe,
  type OutboxItem,
} from "../../lib/outbox";
import { es } from "../../i18n/es";

const commandLabel: Record<OutboxItem["command"], string> = {
  add_income: es.transactions.income,
  add_expense: es.transactions.expense,
  add_transfer: es.transactions.transfer,
};

function itemSummary(item: OutboxItem, currencyByWallet: Map<number, string>): string {
  const a = item.args as Record<string, unknown>;
  const walletId = (a.walletId ?? a.fromWalletId) as number | undefined;
  const cents = (a.amountCents ?? a.amountFromCents) as number | undefined;
  const currency = (walletId !== undefined && currencyByWallet.get(walletId)) || "MXN";
  const amount = cents !== undefined ? formatCents(cents, currency) : "";
  const date = (a.occurredAt as string) ?? "";
  const desc = (a.description as string | null) ?? "";
  return [commandLabel[item.command], amount, date, desc].filter(Boolean).join(" · ");
}

/** Offline captures waiting to sync. Server truth is untouched until they
 *  upload, so they render apart from the real transaction list. */
export function OutboxPanel() {
  const queryClient = useQueryClient();
  const items = useSyncExternalStore(subscribe, getItems);
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  if (items.length === 0) return null;
  const currencyByWallet = new Map(
    (wallets.data ?? []).map((w) => [w.id, w.currencyCode]),
  );

  const retryNow = (id: string) => {
    retryItem(id);
    flush().then((synced) => {
      if (synced > 0) queryClient.invalidateQueries();
    });
  };

  return (
    <section className="mb-4 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
      <h3 className="flex items-center gap-2 text-sm font-medium text-amber-300">
        <CloudOff size={15} />
        {es.offline.pendingTitle} ({items.length})
      </h3>
      <p className="mt-1 text-xs text-zinc-500">{es.offline.pendingHint}</p>
      <ul className="mt-3 divide-y divide-border-muted">
        {items.map((item) => (
          <li key={item.id} className="flex items-center gap-3 py-2 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate text-zinc-200">
                {itemSummary(item, currencyByWallet)}
              </p>
              {item.status === "error" && (
                <p className="truncate text-xs text-danger">
                  {es.offline.syncError}: {item.errorMsg}
                </p>
              )}
            </div>
            {item.status === "error" && (
              <button
                onClick={() => retryNow(item.id)}
                title={es.offline.retry}
                className="rounded-md p-1.5 text-zinc-400 transition-colors hover:bg-surface-overlay hover:text-zinc-200"
              >
                <RotateCw size={15} />
              </button>
            )}
            <button
              onClick={() => discard(item.id)}
              title={es.offline.discard}
              className="rounded-md p-1.5 text-zinc-400 transition-colors hover:bg-danger/10 hover:text-danger"
            >
              <Trash2 size={15} />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
