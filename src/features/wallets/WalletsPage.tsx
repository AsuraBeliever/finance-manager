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
import { GripVertical, Plus, Wallet as WalletIcon } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import { WalletCard } from "../../components/WalletCard";
import { listWallets, reorderWallets } from "../../lib/api";
import type { Wallet } from "../../lib/types";
import { es } from "../../i18n/es";
import { WalletFormModal } from "./WalletFormModal";

/** A wallet card wrapped for drag-to-reorder. Dragging is initiated only from
 *  the grip handle so the rest of the card stays clickable (opens the detail). */
function SortableWalletCard({ wallet }: { wallet: Wallet }) {
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

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = visible.findIndex((w) => w.id === active.id);
    const newIndex = visible.findIndex((w) => w.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const next = arrayMove(visible, oldIndex, newIndex);
    queryClient.setQueryData(["wallets", { showArchived }], next);
    reorder.mutate(next.map((w) => w.id));
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
        <SortableContext items={visible.map((w) => w.id)} strategy={rectSortingStrategy}>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-5">
            {visible.map((w) => (
              <SortableWalletCard key={w.id} wallet={w} />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      <WalletFormModal open={formOpen} onClose={() => setFormOpen(false)} />
    </>
  );
}
