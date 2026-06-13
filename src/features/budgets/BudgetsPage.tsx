import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Plus, Trash2, Wallet } from "lucide-react";
import { Button } from "../../components/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass } from "../../components/Field";
import { Modal } from "../../components/Modal";
import { PageHeader } from "../../components/PageHeader";
import { ProgressBar } from "../../components/ProgressBar";
import {
  deleteBudget,
  listBudgets,
  listTransactionCategories,
  setBudget,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { es } from "../../i18n/es";

export function BudgetsPage() {
  const qc = useQueryClient();
  const budgets = useQuery({ queryKey: ["budgets"], queryFn: listBudgets });
  const [formOpen, setFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["budgets"] });
  const remove = useMutation({ mutationFn: deleteBudget, onSuccess: invalidate });

  const list = budgets.data ?? [];

  return (
    <>
      <PageHeader
        title={es.budgets.title}
        actions={
          <Button onClick={() => setFormOpen(true)}>
            <span className="flex items-center gap-2">
              <Plus size={16} /> {es.budgets.newBudget}
            </span>
          </Button>
        }
      />

      {budgets.isSuccess && list.length === 0 && (
        <EmptyState
          icon={Wallet}
          title={es.budgets.emptyTitle}
          description={es.budgets.emptyDescription}
        />
      )}

      <div className="grid gap-3">
        {list.map((b) => {
          const remaining = b.limitCents - b.spentMxnCents;
          const over = remaining < 0;
          return (
            <section
              key={b.id}
              className="rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card"
            >
              <div className="mb-2 flex items-center justify-between gap-3">
                <span className="flex items-center gap-2 font-medium text-fg">
                  {b.color && (
                    <span className="h-3 w-3 rounded-full" style={{ backgroundColor: b.color }} />
                  )}
                  {b.categoryName ?? es.budgets.overall}
                </span>
                <button
                  onClick={() => setDeleteId(b.id)}
                  className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 size={15} />
                </button>
              </div>
              <ProgressBar
                value={b.progressBps / 10000}
                color={b.color ?? "var(--color-gold)"}
              />
              <div className="mt-2 flex items-center justify-between text-sm tabular-nums">
                <span className="text-fg-muted">
                  {formatCents(b.spentMxnCents, "MXN")} {es.goals.of}{" "}
                  {formatCents(b.limitCents, "MXN")}
                </span>
                <span className={over ? "text-danger" : "text-accent"}>
                  {over ? es.budgets.over : es.budgets.remaining}:{" "}
                  {formatCents(Math.abs(remaining), "MXN")}
                </span>
              </div>
            </section>
          );
        })}
      </div>

      <BudgetFormModal open={formOpen} onClose={() => setFormOpen(false)} onSaved={invalidate} />
      <ConfirmDialog
        open={deleteId !== null}
        title={es.common.delete}
        message={es.budgets.deleteConfirm}
        confirmLabel={es.common.delete}
        onConfirm={() => {
          if (deleteId !== null) remove.mutate(deleteId);
          setDeleteId(null);
        }}
        onClose={() => setDeleteId(null)}
      />
    </>
  );
}

function BudgetFormModal({
  open,
  onClose,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const categories = useQuery({
    queryKey: ["transactionCategories"],
    queryFn: listTransactionCategories,
  });
  const [categoryId, setCategoryId] = useState<string>(""); // "" = overall
  const [limit, setLimit] = useState("");
  const [error, setError] = useState<string | null>(null);

  const expenseCats = (categories.data ?? []).filter((c) => c.kind === "expense");

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(limit);
      if (cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      return setBudget(categoryId === "" ? null : Number(categoryId), cents);
    },
    onSuccess: () => {
      setLimit("");
      setCategoryId("");
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal open={open} onClose={onClose} title={es.budgets.newBudget}>
      <div className="flex flex-col gap-4">
        <Field label={es.budgets.category}>
          <select
            className={inputClass}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">{es.budgets.overall}</option>
            {expenseCats.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label={es.budgets.limit}>
          <input
            className={inputClass}
            inputMode="decimal"
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
          />
        </Field>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {es.common.save}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
