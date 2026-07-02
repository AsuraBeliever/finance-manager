import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  rectSortingStrategy,
  sortableKeyboardCoordinates,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ChevronDown, GripVertical, PiggyBank, Plus, Wallet as WalletIcon } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { WalletCard } from "../../components/WalletCard";
import { listWallets, reorderWallets } from "../../lib/api";
import { formatCents } from "../../lib/money";
import type { Wallet } from "../../lib/types";
import { es } from "../../i18n/es";
import { WalletFormModal } from "./WalletFormModal";

/** A compact row for an apartado (pocket) wallet, nested under its parent. */
function ApartadoRow({ wallet }: { wallet: Wallet }) {
  return (
    <Link
      to={`/carteras/${wallet.id}`}
      className="flex items-center gap-2.5 rounded-xl border border-border-muted bg-surface-raised px-3.5 py-2.5 transition-colors hover:border-accent/40"
    >
      <span
        className="h-6 w-1 shrink-0 rounded-full"
        style={{ background: wallet.color ?? "var(--color-accent)" }}
      />
      <PiggyBank size={15} className="shrink-0 text-fg-subtle" />
      <span className="min-w-0 flex-1 truncate text-sm text-fg">{wallet.name}</span>
      <span className="shrink-0 text-sm font-medium tabular-nums text-fg">
        {formatCents(wallet.balanceCents, wallet.currencyCode)}
      </span>
    </Link>
  );
}

/** A wallet card wrapped for drag-to-reorder. Dragging is initiated only from
 *  the grip handle so the rest of the card stays clickable (opens the detail). */
function SortableWalletCard({ wallet, apartados }: { wallet: Wallet; apartados: Wallet[] }) {
  const [open, setOpen] = useState(false);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: wallet.id,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : undefined,
  };
  return (
    <div ref={setNodeRef} style={style} className={isDragging ? "opacity-90" : undefined}>
      <div className="relative">
        <WalletCard wallet={wallet} />
        <button
          type="button"
          {...attributes}
          {...listeners}
          aria-label={es.wallets.reorder}
          title={es.wallets.reorder}
          className="absolute bottom-3 right-3 cursor-grab touch-none rounded-lg bg-black/25 p-1.5 text-white/80 backdrop-blur-sm transition-colors hover:bg-black/45 hover:text-white active:cursor-grabbing"
        >
          <GripVertical size={16} />
        </button>
      </div>
      {apartados.length > 0 && (
        <div className="mt-2">
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            aria-expanded={open}
            className="flex w-full items-center gap-1.5 rounded-lg px-1 py-1 text-[0.65rem] font-medium uppercase tracking-[0.12em] text-fg-subtle transition-colors hover:text-fg"
          >
            <ChevronDown
              size={14}
              className={`transition-transform duration-300 ${open ? "rotate-180" : ""}`}
            />
            {es.wallets.apartadosLabel} · {apartados.length}
          </button>
          {/* grid-rows trick animates height without measuring the content */}
          <div
            className={`grid transition-all duration-300 ease-out ${
              open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"
            }`}
          >
            <div className="overflow-hidden">
              <div className="space-y-1.5 border-l-2 border-border-muted pl-3 pt-1">
                {apartados.map((a) => (
                  <ApartadoRow key={a.id} wallet={a} />
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export function WalletsPage() {
  const queryClient = useQueryClient();
  const [showArchived, setShowArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const wallets = useQuery({
    queryKey: ["wallets", { showArchived }],
    queryFn: () => listWallets(showArchived),
  });

  const reorder = useMutation({
    mutationFn: (ids: number[]) => reorderWallets(ids),
    // The new order is already shown optimistically; refetch only to recover
    // from a failed save.
    onError: () => queryClient.invalidateQueries({ queryKey: ["wallets"] }),
  });

  const sensors = useSensors(
    // A few px of movement before a drag starts, so a plain tap still opens
    // the card.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const visible = wallets.data ?? [];

  // Split into top-level wallets and their apartados (children). A child whose
  // parent isn't visible (e.g. archived) falls back to top-level so it's shown.
  const visibleIds = new Set(visible.map((w) => w.id));
  const topLevel = visible.filter(
    (w) => w.parentWalletId == null || !visibleIds.has(w.parentWalletId),
  );
  const children = visible.filter(
    (w) => w.parentWalletId != null && visibleIds.has(w.parentWalletId),
  );
  const apartadosOf = (id: number) => children.filter((w) => w.parentWalletId === id);

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = topLevel.findIndex((w) => w.id === active.id);
    const newIndex = topLevel.findIndex((w) => w.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const nextTop = arrayMove(topLevel, oldIndex, newIndex);
    // Grouping is derived from parentWalletId, so array order only needs the new
    // top-level sequence; children ride along and re-nest at render.
    queryClient.setQueryData(["wallets", { showArchived }], [...nextTop, ...children]);
    reorder.mutate(nextTop.map((w) => w.id));
  };

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

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={topLevel.map((w) => w.id)} strategy={rectSortingStrategy}>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] items-start gap-5">
            {topLevel.map((w) => (
              <SortableWalletCard key={w.id} wallet={w} apartados={apartadosOf(w.id)} />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      <WalletFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
