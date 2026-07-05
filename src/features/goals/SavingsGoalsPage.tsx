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
import { useState } from "react";
import { GripVertical, PiggyBank, Plus } from "lucide-react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState";
import { PageHeader } from "../../components/PageHeader";
import {
  deleteSavingsGoal,
  listSavingsGoals,
  listWallets,
  reorderSavingsGoals,
  useSavingsGoal,
} from "../../lib/api";
import { WalletFormModal } from "../wallets/WalletFormModal";
import { formatCents } from "../../lib/money";
import type { SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";
import { ContributeModal } from "./ContributeModal";
import { GoalCard } from "./GoalCard";
import { GoalFormModal } from "./GoalFormModal";

/** A goal card wrapped for drag-to-reorder. Dragging starts from the grip so
 *  the edit/delete/contribute controls stay clickable. The first card (lowest
 *  sort_order) is the dashboard's principal gauge. */
function SortableGoalCard({
  goal: g,
  ...cardProps
}: {
  goal: SavingsGoal;
  walletName: string | null;
  onEdit: () => void;
  onDelete: () => void;
  onContribute: () => void;
  onUse: () => void;
  onConvert: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: g.id,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : undefined,
  };
  return (
    <GoalCard
      goal={g}
      {...cardProps}
      containerRef={setNodeRef}
      style={style}
      dragging={isDragging}
      dragHandle={
        <button
          type="button"
          {...attributes}
          {...listeners}
          aria-label={es.goals.reorder}
          title={es.goals.reorder}
          className="shrink-0 cursor-grab touch-none rounded-md p-1 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg active:cursor-grabbing"
        >
          <GripVertical size={16} />
        </button>
      }
    />
  );
}

export function SavingsGoalsPage() {
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

  const reorder = useMutation({
    mutationFn: (ids: number[]) => reorderSavingsGoals(ids),
    // Refresh every goal query (including the dashboard's period-scoped one, so
    // the principal/gauge follows the new order); the page is already optimistic.
    onSettled: () => qc.invalidateQueries({ queryKey: ["savingsGoals"] }),
  });

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const list = goals.data ?? [];

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = list.findIndex((g) => g.id === active.id);
    const newIndex = list.findIndex((g) => g.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const next = arrayMove(list, oldIndex, newIndex);
    qc.setQueryData(["savingsGoals"], next);
    reorder.mutate(next.map((g) => g.id));
  };

  return (
    <>
      <PageHeader
        title={es.goals.title}
        actions={
          <Button
            onClick={() => {
              setFormGoal(null);
              setFormOpen(true);
            }}
          >
            <span className="flex items-center gap-2">
              <Plus size={16} /> {es.goals.newGoal}
            </span>
          </Button>
        }
      />

      {goals.isSuccess && list.length === 0 && (
        <EmptyState
          icon={PiggyBank}
          title={es.goals.emptyTitle}
          description={es.goals.emptyDescription}
        />
      )}

      <p className="mb-3 text-sm text-fg-subtle">{es.goals.reorderHint}</p>
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={list.map((g) => g.id)} strategy={rectSortingStrategy}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {list.map((g) => (
              <SortableGoalCard
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
        </SortableContext>
      </DndContext>

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
    </>
  );
}
