import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, EyeOff, Pencil, Plus, RotateCcw, Trash2, X } from "lucide-react";
import { useState } from "react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import {
  createTransactionCategory,
  deleteTransactionCategory,
  listManageCategories,
  restoreTransactionCategory,
  updateTransactionCategory,
} from "../../lib/api";
import { CHART_COLORS, NEUTRAL_DOT } from "../../lib/palette";
import type { TransactionCategory } from "../../lib/types";
import { es } from "../../i18n/es";

type Kind = "income" | "expense";

/** A compact swatch row: the chart palette plus a "no color" choice. */
function ColorPicker({
  value,
  onChange,
}: {
  value: string | null;
  onChange: (c: string | null) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <button
        type="button"
        title={es.categories.noColor}
        onClick={() => onChange(null)}
        className={`flex h-5 w-5 items-center justify-center rounded-full border border-border-muted text-fg-subtle ${
          value === null ? "ring-2 ring-accent ring-offset-1 ring-offset-surface-raised" : ""
        }`}
      >
        <X size={11} />
      </button>
      {CHART_COLORS.map((c) => (
        <button
          key={c}
          type="button"
          onClick={() => onChange(c)}
          style={{ backgroundColor: c }}
          className={`h-5 w-5 rounded-full ${
            value === c ? "ring-2 ring-accent ring-offset-1 ring-offset-surface-raised" : ""
          }`}
        />
      ))}
    </div>
  );
}

function CategoryRow({
  cat,
  onAskDelete,
}: {
  cat: TransactionCategory;
  onAskDelete: (cat: TransactionCategory) => void;
}) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(cat.name);
  const [color, setColor] = useState<string | null>(cat.color);

  const invalidate = () => {
    for (const k of [
      "manageCategories",
      "transactionCategories",
      "transactions",
      "dashboard",
      "breakdown",
      "budgets",
    ]) {
      queryClient.invalidateQueries({ queryKey: [k] });
    }
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
      <li className="flex flex-col gap-2 py-2">
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
    <li className={`flex items-center gap-2.5 py-2 ${cat.isHidden ? "opacity-50" : ""}`}>
      <span
        className="h-2.5 w-2.5 shrink-0 rounded-full"
        style={{ backgroundColor: cat.color ?? NEUTRAL_DOT }}
      />
      <span className="min-w-0 flex-1 truncate text-sm text-fg">{cat.name}</span>
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

function AddCategory({ kind }: { kind: Kind }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const add = useMutation({
    mutationFn: () => createTransactionCategory(name.trim(), kind),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["manageCategories"] });
      queryClient.invalidateQueries({ queryKey: ["transactionCategories"] });
      setName("");
    },
  });
  return (
    <div className="mt-2 flex items-center gap-2">
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

  const del = useMutation({
    mutationFn: (id: number) => deleteTransactionCategory(id),
    onSuccess: () => {
      for (const k of [
        "manageCategories",
        "transactionCategories",
        "transactions",
        "dashboard",
        "breakdown",
        "budgets",
      ]) {
        queryClient.invalidateQueries({ queryKey: [k] });
      }
      setToDelete(null);
    },
  });

  return (
    <section>
      <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
        {kind === "income" ? es.categories.income : es.categories.expense}
      </h4>
      {cats.length === 0 ? (
        <p className="py-2 text-sm text-fg-subtle">{es.categories.empty}</p>
      ) : (
        <ul className="divide-y divide-border-muted">
          {cats.map((c) => (
            <CategoryRow key={c.id} cat={c} onAskDelete={setToDelete} />
          ))}
        </ul>
      )}
      <AddCategory kind={kind} />

      <ConfirmDialog
        open={toDelete !== null}
        title={es.categories.deleteConfirmTitle}
        message={
          toDelete?.isSystem
            ? es.categories.hideConfirmMessage
            : es.categories.deleteConfirmMessage
        }
        confirmLabel={toDelete?.isSystem ? es.categories.hide : es.categories.delete}
        onConfirm={() => toDelete && del.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </section>
  );
}

export function CategoryManagerModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const cats = useQuery({ queryKey: ["manageCategories"], queryFn: listManageCategories });
  const list = cats.data ?? [];

  return (
    <Modal title={es.categories.title} open={open} onClose={onClose}>
      {cats.isPending && <p className="text-sm text-fg-subtle">{es.common.loading}</p>}
      {cats.isError && <p className="text-sm text-danger">{String(cats.error)}</p>}
      <div className="flex flex-col gap-6">
        <Group kind="income" cats={list.filter((c) => c.kind === "income")} />
        <Group kind="expense" cats={list.filter((c) => c.kind === "expense")} />
      </div>
    </Modal>
  );
}
