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
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Check, EyeOff, GripVertical, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ColorPicker } from "../../components/ColorPicker";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { inputClass } from "../../components/Field";
import { PageHeader } from "../../components/PageHeader";
import {
  createTransactionCategory,
  deleteTransactionCategory,
  listManageCategories,
  reorderTransactionCategories,
  restoreTransactionCategory,
  updateTransactionCategory,
} from "../../lib/api";
import { CATEGORY_PALETTE, NEUTRAL_DOT } from "../../lib/palette";
import type { TransactionCategory } from "../../lib/types";
import { es } from "../../i18n/es";
import { seedName } from "../../i18n/seed";

type Kind = "income" | "expense";

// Any cache that renders a category name/color or aggregates by category.
const AFFECTED_KEYS = [
  "manageCategories",
  "transactionCategories",
  "transactions",
  "dashboard",
  "breakdown",
  "budgets",
];

/** First palette color not already used by an existing category, so new ones
 *  come out a different color; cycles once the palette is exhausted. */
function nextColor(existing: TransactionCategory[]): string {
  const used = new Set(existing.map((c) => c.color));
  return CATEGORY_PALETTE.find((c) => !used.has(c)) ?? CATEGORY_PALETTE[existing.length % CATEGORY_PALETTE.length];
}

function CategoryRow({
  cat,
  onAskDelete,
}: {
  cat: TransactionCategory;
  onAskDelete: (cat: TransactionCategory) => void;
}) {
  const queryClient = useQueryClient();
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: cat.id,
  });
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(cat.name);
  const [color, setColor] = useState<string | null>(cat.color);

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : undefined,
  };

  const invalidate = () => {
    for (const k of AFFECTED_KEYS) queryClient.invalidateQueries({ queryKey: [k] });
  };

  const save = useMutation({
    mutationFn: () => updateTransactionCategory(cat.id, name.trim(), color),
    onSuccess: () => {
      invalidate();
      setEditing(false);
    },
  });
  const restore = useMutation({
    mutationFn: () => restoreTransactionCategory(cat.id),
    onSuccess: invalidate,
  });

  if (editing) {
    return (
      <li ref={setNodeRef} style={style} className="flex flex-col gap-2 py-2.5">
        <div className="flex items-center gap-2">
          <input
            autoFocus
            className={inputClass}
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && name.trim() && save.mutate()}
          />
          <button
            type="button"
            aria-label={es.categories.save}
            disabled={!name.trim() || save.isPending}
            onClick={() => save.mutate()}
            className="rounded-md p-1.5 text-accent hover:bg-surface-overlay disabled:opacity-40"
          >
            <Check size={16} />
          </button>
          <button
            type="button"
            aria-label={es.common.cancel}
            onClick={() => {
              setEditing(false);
              setName(cat.name);
              setColor(cat.color);
            }}
            className="rounded-md p-1.5 text-fg-muted hover:bg-surface-overlay"
          >
            <X size={16} />
          </button>
        </div>
        <ColorPicker value={color} onChange={setColor} />
      </li>
    );
  }

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={`flex items-center gap-2 py-2.5 ${cat.isHidden ? "opacity-50" : ""}`}
    >
      <button
        type="button"
        aria-label={es.categories.reorder}
        title={es.categories.reorder}
        {...attributes}
        {...listeners}
        className="cursor-grab touch-none rounded-md p-1 text-fg-subtle hover:text-fg active:cursor-grabbing"
      >
        <GripVertical size={15} />
      </button>
      <span
        className="h-2.5 w-2.5 shrink-0 rounded-full"
        style={{ backgroundColor: cat.color ?? NEUTRAL_DOT }}
      />
      <span className="min-w-0 flex-1 truncate text-sm text-fg">{seedName(cat.name, cat.isSystem)}</span>
      {cat.isSystem && (
        <span className="shrink-0 rounded-full bg-surface-overlay px-2 py-0.5 text-[10px] text-fg-subtle">
          {cat.isHidden ? es.categories.hiddenLabel : es.categories.defaultBadge}
        </span>
      )}
      {cat.isHidden ? (
        <button
          type="button"
          aria-label={es.categories.restore}
          title={es.categories.restore}
          onClick={() => restore.mutate()}
          className="rounded-md p-1.5 text-fg-muted hover:bg-surface-overlay hover:text-fg"
        >
          <RotateCcw size={15} />
        </button>
      ) : (
        <>
          {!cat.isSystem && (
            <button
              type="button"
              aria-label={es.categories.rename}
              title={es.categories.rename}
              onClick={() => setEditing(true)}
              className="rounded-md p-1.5 text-fg-muted hover:bg-surface-overlay hover:text-fg"
            >
              <Pencil size={15} />
            </button>
          )}
          <button
            type="button"
            aria-label={cat.isSystem ? es.categories.hide : es.categories.delete}
            title={cat.isSystem ? es.categories.hide : es.categories.delete}
            onClick={() => onAskDelete(cat)}
            className="rounded-md p-1.5 text-fg-muted hover:bg-surface-overlay hover:text-danger"
          >
            {cat.isSystem ? <EyeOff size={15} /> : <Trash2 size={15} />}
          </button>
        </>
      )}
    </li>
  );
}

function AddCategory({ kind, existing }: { kind: Kind; existing: TransactionCategory[] }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const add = useMutation({
    mutationFn: () => createTransactionCategory(name.trim(), kind, nextColor(existing)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["manageCategories"] });
      queryClient.invalidateQueries({ queryKey: ["transactionCategories"] });
      setName("");
    },
  });
  return (
    <div className="mt-3 flex items-center gap-2">
      <input
        className={inputClass}
        placeholder={es.categories.addPlaceholder}
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && name.trim() && add.mutate()}
      />
      <Button
        variant="ghost"
        className="shrink-0 px-3 py-2"
        disabled={!name.trim() || add.isPending}
        onClick={() => add.mutate()}
      >
        <span className="flex items-center gap-1.5">
          <Plus size={15} /> {es.categories.add}
        </span>
      </Button>
    </div>
  );
}

function Group({ kind, cats }: { kind: Kind; cats: TransactionCategory[] }) {
  const queryClient = useQueryClient();
  const [toDelete, setToDelete] = useState<TransactionCategory | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const del = useMutation({
    mutationFn: (id: number) => deleteTransactionCategory(id),
    onSuccess: () => {
      for (const k of AFFECTED_KEYS) queryClient.invalidateQueries({ queryKey: [k] });
      setToDelete(null);
    },
  });

  const reorder = useMutation({
    mutationFn: (ids: number[]) => reorderTransactionCategories(ids),
    onError: () => queryClient.invalidateQueries({ queryKey: ["manageCategories"] }),
  });

  const onDragEnd = (e: DragEndEvent) => {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = cats.findIndex((c) => c.id === active.id);
    const newIndex = cats.findIndex((c) => c.id === over.id);
    if (oldIndex < 0 || newIndex < 0) return;
    const next = arrayMove(cats, oldIndex, newIndex);
    queryClient.setQueryData<TransactionCategory[]>(["manageCategories"], (old) => {
      const rest = (old ?? []).filter((c) => c.kind !== kind);
      return [...rest, ...next];
    });
    reorder.mutate(next.map((c) => c.id));
  };

  return (
    <section className="rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 font-medium">
        {kind === "income" ? es.categories.income : es.categories.expense}
      </h3>
      {cats.length === 0 ? (
        <p className="py-2 text-sm text-fg-subtle">{es.categories.empty}</p>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext items={cats.map((c) => c.id)} strategy={verticalListSortingStrategy}>
            <ul className="divide-y divide-border-muted">
              {cats.map((c) => (
                <CategoryRow key={c.id} cat={c} onAskDelete={setToDelete} />
              ))}
            </ul>
          </SortableContext>
        </DndContext>
      )}
      <AddCategory kind={kind} existing={cats} />

      <ConfirmDialog
        open={toDelete !== null}
        title={es.categories.deleteConfirmTitle}
        message={
          toDelete?.isSystem ? es.categories.hideConfirmMessage : es.categories.deleteConfirmMessage
        }
        confirmLabel={toDelete?.isSystem ? es.categories.hide : es.categories.delete}
        onConfirm={() => toDelete && del.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </section>
  );
}

export function CategoriesPage() {
  const cats = useQuery({ queryKey: ["manageCategories"], queryFn: listManageCategories });
  const list = cats.data ?? [];

  return (
    <div className="mx-auto w-full max-w-2xl">
      <PageHeader title={es.categories.title} />
      <p className="mb-5 -mt-3 text-sm text-fg-subtle">{es.categories.settingsHint}</p>

      {cats.isPending && <p className="text-sm text-fg-subtle">{es.common.loading}</p>}
      {cats.isError && <p className="text-sm text-danger">{String(cats.error)}</p>}

      <div className="flex flex-col gap-6">
        <Group kind="income" cats={list.filter((c) => c.kind === "income")} />
        <Group kind="expense" cats={list.filter((c) => c.kind === "expense")} />
      </div>
    </div>
  );
}
