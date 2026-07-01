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
import { useState, type ReactNode } from "react";
import { Check, GripVertical, Pencil, PiggyBank, Plus, Trash2, Wallet } from "lucide-react";
import { Button } from "../../components/Button";
import { ColorPicker } from "../../components/ColorPicker";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { DateInput } from "../../components/DateInput";
import { EmptyState } from "../../components/EmptyState";
import { Field, inputClass } from "../../components/Field";
import { MoneyInput } from "../../components/MoneyInput";
import { ProgressBar } from "../../components/ProgressBar";
import { Modal } from "../../components/Modal";
import { PageHeader } from "../../components/PageHeader";
import {
  contributeSavingsGoal,
  convertGoalToWallet,
  createSavingsGoal,
  deleteSavingsGoal,
  listSavingsGoals,
  listWallets,
  reorderSavingsGoals,
  updateSavingsGoal,
  useSavingsGoal,
} from "../../lib/api";
import { formatCents, parseToCents } from "../../lib/money";
import { CHART_COLORS } from "../../lib/palette";
import type { GoalCadence, GoalKind, SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";

/** Today's date as ISO 'YYYY-MM-DD' (the earliest selectable deadline). */
function todayISO(): string {
  return new Date().toISOString().slice(0, 10);
}

/** A sensible default deadline (~3 months out) prefilled when the user turns
 *  on a deadline, so they can just tweak it instead of starting from scratch. */
function defaultDeadlineISO(): string {
  const d = new Date();
  d.setMonth(d.getMonth() + 3);
  return d.toISOString().slice(0, 10);
}

/** Short, locale-aware date like "30 nov 2026" for the plan line. */
function formatDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/** Adverbial cadence ("al mes" / "a month") for the plan sentence. */
function cadenceAdverb(c: GoalCadence | null): string {
  switch (c) {
    case "daily":
      return es.goals.cadenceAdvDaily;
    case "weekly":
      return es.goals.cadenceAdvWeekly;
    case "yearly":
      return es.goals.cadenceAdvYearly;
    default:
      return es.goals.cadenceAdvMonthly;
  }
}

/** A goal card wrapped for drag-to-reorder. Dragging starts from the grip so
 *  the edit/delete/contribute controls stay clickable. The first card (lowest
 *  sort_order) is the dashboard's principal gauge. */
function SortableGoalCard({
  goal: g,
  walletName,
  onEdit,
  onDelete,
  onContribute,
  onUse,
  onConvert,
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
  const done = g.progressBps >= 10000;
  const remaining = Math.max(0, g.targetCents - g.savedCents);
  const color = g.color ?? "var(--color-accent)";
  return (
    <section
      ref={setNodeRef}
      style={style}
      className={`group rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card transition-colors duration-300 hover:border-accent/40 ${
        isDragging ? "opacity-90" : ""
      }`}
    >
      <div className="mb-4 flex items-center gap-2">
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
        <span
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
          style={{ backgroundColor: `color-mix(in oklab, ${color} 22%, transparent)` }}
        >
          <PiggyBank size={17} style={{ color }} />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-display text-lg font-medium text-fg">{g.name}</h3>
          <p className="truncate text-xs text-fg-subtle">
            {walletName ? `${es.goals.apartadoIn} ${walletName}` : es.goals.trackOnly}
          </p>
        </div>
        <div className="touch-action-reveal flex shrink-0 gap-1 transition-opacity">
          <button
            onClick={onEdit}
            className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
          >
            <Pencil size={15} />
          </button>
          <button
            onClick={onDelete}
            className="rounded-md p-1.5 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </div>

      <p className="font-display text-2xl font-semibold tabular-nums text-fg">
        {formatCents(g.savedCents, g.currencyCode)}
        <span className="ml-1.5 text-sm font-normal text-fg-subtle">
          {es.goals.of} {formatCents(g.targetCents, g.currencyCode)}
        </span>
      </p>

      <ProgressBar className="mt-3" value={g.progressBps / 10000} color={color} />

      <div className="mt-3 flex items-center justify-between">
        <span className="text-sm tabular-nums">
          <span className="font-semibold text-accent">
            {done ? es.goals.completed : `${Math.round(g.progressBps / 100)}%`}
          </span>
          {!done && (
            <span className="text-fg-subtle">
              {" · "}
              {es.goals.remaining} {formatCents(remaining, g.currencyCode)}
            </span>
          )}
        </span>
        <div className="flex shrink-0 gap-1">
          <Button variant="ghost" onClick={onContribute}>
            {es.goals.contribute}
          </Button>
          {g.savedCents > 0 &&
            (g.goalKind === "fund" ? (
              <Button variant="ghost" onClick={onConvert}>
                <span className="flex items-center gap-1.5">
                  <Wallet size={14} /> {es.goals.convertToWallet}
                </span>
              </Button>
            ) : (
              <Button variant="ghost" onClick={onUse}>
                <span className="flex items-center gap-1.5">
                  <Check size={14} /> {es.goals.buy}
                </span>
              </Button>
            ))}
        </div>
      </div>

      {g.plan && !done && (
        <div className="mt-3 border-t border-border-muted pt-3 text-xs">
          {g.plan.overdue ? (
            <p className="text-fg-subtle">
              <BadgeTag tone="danger">{es.goals.overdueBadge}</BadgeTag>{" "}
              {es.goals.overdueHint.replace(
                "{amount}",
                formatCents(remaining, g.currencyCode),
              )}
            </p>
          ) : (
            <>
              <p className="text-fg-muted">
                {es.goals.planReserve
                  .replace("{amount}", formatCents(g.plan.perPeriodCents, g.currencyCode))
                  .replace("{cadence}", cadenceAdverb(g.cadence))
                  .replace("{date}", formatDate(g.targetDate ?? ""))}
              </p>
              {g.isBehind && (
                <p className="mt-1 text-fg-subtle">
                  <BadgeTag tone="warning">{es.goals.behindBadge}</BadgeTag>{" "}
                  {es.goals.behindHint.replace(
                    "{amount}",
                    formatCents(g.plan.behindCents, g.currencyCode),
                  )}
                </p>
              )}
            </>
          )}
          {(g.plan.overdue || g.isBehind) && (
            <button
              onClick={onEdit}
              className="mt-2 font-medium text-accent transition-colors hover:text-accent/80"
            >
              {es.goals.adjustDate}
            </button>
          )}
        </div>
      )}
    </section>
  );
}

/** Tiny inline status pill used by goal cards (behind / overdue). */
function BadgeTag({ tone, children }: { tone: "warning" | "danger"; children: ReactNode }) {
  const cls =
    tone === "danger"
      ? "bg-danger/12 text-danger"
      : "bg-amber-500/15 text-amber-600 dark:text-amber-400";
  return (
    <span className={`mr-0.5 inline-block rounded-md px-1.5 py-0.5 font-semibold ${cls}`}>
      {children}
    </span>
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
  const convertMut = useMutation({
    mutationFn: (id: number) => convertGoalToWallet(id),
    onSuccess: invalidate,
    onSettled: () => setConvertGoal(null),
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
      <ConfirmDialog
        open={convertGoal !== null}
        title={es.goals.convertToWallet}
        message={
          convertGoal
            ? es.goals.convertConfirm
                .replace("{name}", convertGoal.name)
                .replace("{amount}", formatCents(convertGoal.savedCents, convertGoal.currencyCode))
                .replace("{wallet}", walletName(convertGoal.linkedWalletId) ?? "")
            : ""
        }
        confirmLabel={es.goals.convertToWallet}
        onConfirm={() => {
          if (convertGoal) convertMut.mutate(convertGoal.id);
        }}
        onClose={() => setConvertGoal(null)}
      />
    </>
  );
}

function GoalFormModal({
  open,
  goal,
  onClose,
  onSaved,
}: {
  open: boolean;
  goal: SavingsGoal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });
  const [name, setName] = useState("");
  const [target, setTarget] = useState("");
  const [currency, setCurrency] = useState("MXN");
  const [walletId, setWalletId] = useState<number | null>(null);
  const [goalKind, setGoalKind] = useState<GoalKind>("purchase");
  const [color, setColor] = useState<string>(CHART_COLORS[0]);
  const [deadlineEnabled, setDeadlineEnabled] = useState(false);
  const [deadline, setDeadline] = useState<string>("");
  const [cadence, setCadence] = useState<GoalCadence>("monthly");
  const [error, setError] = useState<string | null>(null);

  // Reset fields whenever the modal opens for a new/edited goal. New goals
  // default to the first wallet (every goal is now backed by real money).
  const [lastKey, setLastKey] = useState<string>("");
  const key = `${open}-${goal?.id ?? "new"}`;
  if (open && key !== lastKey) {
    setLastKey(key);
    setName(goal?.name ?? "");
    setTarget(goal ? (goal.targetCents / 100).toString() : "");
    setCurrency(goal?.currencyCode ?? "MXN");
    setWalletId(goal?.linkedWalletId ?? wallets.data?.[0]?.id ?? null);
    setGoalKind(goal?.goalKind ?? "purchase");
    setColor(goal?.color ?? CHART_COLORS[0]);
    setDeadlineEnabled(goal?.targetDate != null);
    setDeadline(goal?.targetDate ?? "");
    setCadence(goal?.cadence ?? "monthly");
    setError(null);
  }

  // An apartado goal follows its wallet's currency.
  const linkedWallet = wallets.data?.find((w) => w.id === walletId) ?? null;
  const effectiveCurrency = linkedWallet?.currencyCode ?? currency;

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(target);
      if (!name.trim() || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      if (walletId == null) return Promise.reject(new Error(es.goals.apartadoWallet));
      const input = {
        name: name.trim(),
        icon: null,
        color,
        currencyCode: effectiveCurrency,
        targetCents: cents,
        walletId,
        targetDate: deadlineEnabled && deadline ? deadline : null,
        cadence: deadlineEnabled && deadline ? cadence : null,
        goalKind,
      };
      return goal ? updateSavingsGoal(goal.id, input) : createSavingsGoal(input);
    },
    onSuccess: () => {
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  return (
    <Modal open={open} onClose={onClose} title={goal ? es.goals.editGoal : es.goals.newGoal}>
      <div className="flex flex-col gap-4">
        <Field label={es.goals.name}>
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label={es.goals.kindLabel}>
          <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
            {(
              [
                ["purchase", es.goals.kindPurchase],
                ["fund", es.goals.kindFund],
              ] as const
            ).map(([val, label]) => (
              <button
                key={val}
                type="button"
                onClick={() => setGoalKind(val)}
                className={`flex-1 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  goalKind === val
                    ? "bg-surface-raised text-fg shadow-sm"
                    : "text-fg-subtle hover:text-fg"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          <span className="mt-1 block text-xs text-fg-subtle">
            {goalKind === "fund" ? es.goals.kindFundHint : es.goals.kindPurchaseHint}
          </span>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={es.goals.target}>
            <MoneyInput value={target} onChange={setTarget} />
          </Field>
          <Field label={es.investments.currency}>
            <input className={inputClass} value={effectiveCurrency} disabled readOnly />
          </Field>
        </div>
        <Field label={es.goals.apartadoWallet}>
          <select
            className={inputClass}
            value={walletId ?? ""}
            onChange={(e) => setWalletId(e.target.value === "" ? null : Number(e.target.value))}
          >
            {wallets.data?.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} ({w.currencyCode})
              </option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-fg-subtle">{es.goals.apartadoHint}</span>
        </Field>
        {/* Deadline is opt-in via a clear switch; flipping it on reveals the
            date + cadence right away (prefilled), so it's configured in place. */}
        <div className="rounded-lg border border-border-muted p-3">
          <label className="flex cursor-pointer items-start gap-2.5">
            <input
              type="checkbox"
              checked={deadlineEnabled}
              onChange={(e) => {
                const on = e.target.checked;
                setDeadlineEnabled(on);
                if (on && !deadline) setDeadline(defaultDeadlineISO());
              }}
              className="mt-0.5 h-4 w-4 accent-accent"
            />
            <span className="text-sm">
              {es.goals.enableDeadline}
              <span className="mt-0.5 block text-xs text-fg-subtle">
                {es.goals.deadlineHint}
              </span>
            </span>
          </label>

          {deadlineEnabled && (
            <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label={es.goals.deadlineDateLabel}>
                <DateInput value={deadline} onChange={setDeadline} min={todayISO()} />
              </Field>
              <Field label={es.goals.cadenceLabel}>
                <select
                  className={inputClass}
                  value={cadence}
                  onChange={(e) => setCadence(e.target.value as GoalCadence)}
                >
                  <option value="daily">{es.goals.cadenceDaily}</option>
                  <option value="weekly">{es.goals.cadenceWeekly}</option>
                  <option value="monthly">{es.goals.cadenceMonthly}</option>
                  <option value="yearly">{es.goals.cadenceYearly}</option>
                </select>
              </Field>
            </div>
          )}
        </div>
        <Field label={es.common.color ?? "Color"}>
          <ColorPicker value={color} onChange={setColor} />
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

function ContributeModal({
  goal,
  onClose,
  onSaved,
}: {
  goal: SavingsGoal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  // Two clear actions, both with positive amounts: "reserve" adds money to the
  // goal, "release" returns it. The backend still takes a signed delta — we
  // negate behind the scenes so the user never types a negative number.
  const [mode, setMode] = useState<"reserve" | "release">("reserve");
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const wallets = useQuery({ queryKey: ["wallets", {}], queryFn: () => listWallets() });

  // Reset whenever the modal opens for a different goal.
  const [lastId, setLastId] = useState<number | null>(null);
  if (goal && goal.id !== lastId) {
    setLastId(goal.id);
    setMode("reserve");
    setAmount("");
    setError(null);
  }

  const save = useMutation({
    mutationFn: () => {
      const cents = parseToCents(amount);
      if (!goal || cents === null || cents <= 0)
        return Promise.reject(new Error(es.investments.invalidAmount));
      return contributeSavingsGoal(goal.id, mode === "release" ? -cents : cents);
    },
    onSuccess: () => {
      setAmount("");
      onSaved();
      onClose();
    },
    onError: (e) => setError(e instanceof Error ? e.message : String(e)),
  });

  // For an apartado goal, show how much is free to reserve in its wallet.
  const apartadoWallet = goal?.linkedWalletId
    ? (wallets.data?.find((w) => w.id === goal.linkedWalletId) ?? null)
    : null;
  const available = apartadoWallet
    ? apartadoWallet.balanceCents - apartadoWallet.reservedCents
    : null;
  const currency = goal?.currencyCode ?? "MXN";
  const canRelease = (goal?.savedCents ?? 0) > 0;
  const suggested = mode === "reserve" ? (goal?.plan?.perPeriodCents ?? 0) : 0;

  const tab = (m: "reserve" | "release", label: string) => (
    <button
      type="button"
      onClick={() => setMode(m)}
      className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
        mode === m
          ? "bg-surface-raised text-fg shadow-sm"
          : "text-fg-subtle hover:text-fg"
      }`}
    >
      {label}
    </button>
  );

  return (
    <Modal open={goal !== null} onClose={onClose} title={es.goals.contributeTitle}>
      <div className="flex flex-col gap-4">
        <div className="flex gap-1 rounded-xl bg-surface-overlay p-1">
          {tab("reserve", es.goals.reserveTab)}
          {canRelease && tab("release", es.goals.releaseTab)}
        </div>

        {mode === "reserve" && apartadoWallet && available !== null && (
          <p className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
            {es.goals.apartadoOf} <span className="font-medium text-fg">{apartadoWallet.name}</span> ·{" "}
            {es.goals.available}{" "}
            <span className="font-medium tabular-nums text-fg">
              {formatCents(available, apartadoWallet.currencyCode)}
            </span>
          </p>
        )}
        {mode === "release" && goal && (
          <p className="rounded-lg bg-surface-overlay px-3 py-2 text-xs text-fg-muted">
            {es.goals.reservedLabel}{" "}
            <span className="font-medium tabular-nums text-fg">
              {formatCents(goal.savedCents, currency)}
            </span>
          </p>
        )}

        <Field label={es.goals.amount}>
          <MoneyInput value={amount} onChange={setAmount} autoFocus />
        </Field>
        {suggested > 0 && (
          <button
            type="button"
            onClick={() => setAmount((suggested / 100).toString())}
            className="-mt-2 self-start rounded-lg bg-accent/10 px-3 py-1.5 text-xs font-medium text-accent transition-colors hover:bg-accent/20"
          >
            {es.goals.suggestedChip} {formatCents(suggested, currency)}
          </button>
        )}
        <p className="-mt-1 text-xs text-fg-subtle">
          {mode === "reserve"
            ? apartadoWallet
              ? es.goals.reserveHint
              : es.goals.reserveTrackHint
            : apartadoWallet
              ? es.goals.releaseHint
              : es.goals.releaseTrackHint}
        </p>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            {es.common.cancel}
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {mode === "reserve" ? es.goals.reserveAction : es.goals.releaseAction}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
