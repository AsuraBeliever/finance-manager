import type { CSSProperties, ReactNode, Ref } from "react";
import { Check, Pencil, PiggyBank, Trash2, Wallet } from "lucide-react";
import { Button } from "../../components/Button";
import { ProgressBar } from "../../components/ProgressBar";
import { formatCents } from "../../lib/money";
import type { GoalCadence, SavingsGoal } from "../../lib/types";
import { es } from "../../i18n/es";

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

/** Period noun ("Este mes" / "This week") for the progress sentence. */
function periodNoun(c: GoalCadence | null): string {
  switch (c) {
    case "daily":
      return es.goals.periodDaily;
    case "weekly":
      return es.goals.periodWeekly;
    case "yearly":
      return es.goals.periodYearly;
    default:
      return es.goals.periodMonthly;
  }
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

/** A savings-goal card: progress, plan line and the contribute/use/convert
 *  actions. Shared by the goals page (wrapped for drag-to-reorder via
 *  `dragHandle`) and the wallet detail page (plain). */
export function GoalCard({
  goal: g,
  walletName,
  onEdit,
  onDelete,
  onContribute,
  onUse,
  onConvert,
  dragHandle,
  containerRef,
  style,
  dragging = false,
}: {
  goal: SavingsGoal;
  walletName: string | null;
  onEdit: () => void;
  onDelete: () => void;
  onContribute: () => void;
  onUse: () => void;
  onConvert: () => void;
  /** Optional grip element rendered before the icon (goals page reorder). */
  dragHandle?: ReactNode;
  containerRef?: Ref<HTMLElement>;
  style?: CSSProperties;
  dragging?: boolean;
}) {
  const done = g.progressBps >= 10000;
  const remaining = Math.max(0, g.targetCents - g.savedCents);
  const color = g.color ?? "var(--color-accent)";
  return (
    <section
      ref={containerRef}
      style={style}
      className={`group rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card transition-colors duration-300 hover:border-accent/40 ${
        dragging ? "opacity-90" : ""
      }`}
    >
      <div className="mb-4 flex items-center gap-2">
        {dragHandle}
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
                {/* The period quota is frozen at the period start: partial money
                    reads as progress ("2,000 of 2,400"), never a re-spread plan. */}
                {g.plan.contributedThisPeriodCents <= 0
                  ? es.goals.planReserve
                      .replace(
                        "{amount}",
                        formatCents(g.plan.periodQuotaCents, g.currencyCode),
                      )
                      .replace("{cadence}", cadenceAdverb(g.cadence))
                      .replace("{date}", formatDate(g.targetDate ?? ""))
                  : g.plan.periodMissingCents > 0
                    ? es.goals.planProgress
                        .replace("{period}", periodNoun(g.cadence))
                        .replace(
                          "{done}",
                          formatCents(g.plan.contributedThisPeriodCents, g.currencyCode),
                        )
                        .replace(
                          "{quota}",
                          formatCents(g.plan.periodQuotaCents, g.currencyCode),
                        )
                        .replace(
                          "{missing}",
                          formatCents(g.plan.periodMissingCents, g.currencyCode),
                        )
                    : es.goals.planCovered
                        .replace("{period}", periodNoun(g.cadence))
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
