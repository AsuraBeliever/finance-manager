import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "../../components/PageHeader";
import {
  getSetting,
  listInvestmentReminders,
  listInvestments,
  setInvestmentReminder,
  setSetting,
} from "../../lib/api";
import type { ReminderCadence, ReminderKind } from "../../lib/types";
import { es } from "../../i18n/es";
import {
  CATEGORY_IDS,
  mergePrefs,
  PREFS_KEY,
  RULE_CATALOG,
  type CategoryId,
  type NotificationPrefs,
  type RulePrefs,
} from "./prefs";

function ruleLabel(cat: CategoryId, ruleId: string): string {
  const key = cat.replace("subscriptions", "sub").replace("investments", "inv").replace("goals", "goal") +
    ruleId.charAt(0).toUpperCase() +
    ruleId.slice(1);
  const labels = es.notifications.rules as Record<string, string>;
  return labels[key] ?? ruleId;
}

export function NotificationSettingsPage() {
  const saved = useQuery({
    queryKey: ["settings", PREFS_KEY],
    queryFn: () => getSetting(PREFS_KEY),
  });
  const [prefs, setPrefs] = useState<NotificationPrefs | null>(null);
  useEffect(() => {
    if (!saved.isPending) setPrefs(mergePrefs(saved.data ?? null));
  }, [saved.isPending, saved.data]);

  const save = useMutation({
    mutationFn: (next: NotificationPrefs) =>
      setSetting(PREFS_KEY, JSON.stringify(next)),
  });

  const update = (fn: (p: NotificationPrefs) => void) => {
    if (!prefs) return;
    const next = structuredClone(prefs);
    fn(next);
    setPrefs(next);
    save.mutate(next);
  };

  if (!prefs) {
    return (
      <div className="mx-auto w-full max-w-3xl">
        <PageHeader
          title={es.notifications.title}
          backTo="/ajustes"
          backLabel={es.settings.back}
        />
        <p className="text-sm text-fg-subtle">{es.common.loading}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-3xl">
      <PageHeader
        title={es.notifications.title}
        backTo="/ajustes"
        backLabel={es.settings.back}
      />
      <p className="mb-6 max-w-xl text-sm text-fg-muted">{es.notifications.pageHint}</p>

      <div className="grid gap-4 lg:grid-cols-2">
        {CATEGORY_IDS.map((cat) => {
          const category = prefs[cat];
          const catLabels = es.notifications.categories as Record<string, string>;
          return (
            <section
              key={cat}
              className="rounded-xl border border-border-muted bg-surface-raised p-5"
            >
              <label className="flex cursor-pointer items-center gap-2.5">
                <input
                  type="checkbox"
                  checked={category.enabled}
                  onChange={(e) =>
                    update((p) => {
                      p[cat].enabled = e.target.checked;
                    })
                  }
                  className="h-4 w-4 accent-accent"
                />
                <h3 className="font-display text-base font-medium text-fg">
                  {catLabels[cat]}
                </h3>
              </label>

              <div
                className={`mt-4 flex flex-col gap-3 ${
                  category.enabled ? "" : "pointer-events-none opacity-50"
                }`}
              >
                {RULE_CATALOG[cat].map((spec) => {
                  const rule: RulePrefs = category.rules[spec.id];
                  return (
                    <div key={spec.id} className="flex flex-wrap items-center gap-2">
                      <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-sm text-fg-muted">
                        <input
                          type="checkbox"
                          checked={rule.enabled}
                          disabled={!category.enabled}
                          onChange={(e) =>
                            update((p) => {
                              p[cat].rules[spec.id].enabled = e.target.checked;
                            })
                          }
                          className="accent-accent"
                        />
                        {ruleLabel(cat, spec.id)}
                      </label>
                      {spec.daysBefore !== undefined && (
                        <span className="flex items-center gap-1.5 text-xs text-fg-subtle">
                          <input
                            type="text"
                            inputMode="numeric"
                            value={rule.daysBefore ?? spec.daysBefore}
                            disabled={!category.enabled || !rule.enabled}
                            onChange={(e) => {
                              const digits = e.target.value.replace(/\D/g, "").slice(0, 2);
                              update((p) => {
                                p[cat].rules[spec.id].daysBefore = Math.min(
                                  60,
                                  Number(digits) || 0,
                                );
                              });
                            }}
                            className="w-14 rounded-lg border border-border-muted bg-surface px-2 py-1 text-center text-xs text-fg disabled:opacity-50"
                          />
                          {es.notifications.daysBefore}
                        </span>
                      )}
                      {spec.thresholdBps !== undefined && (
                        <span className="flex items-center gap-1.5 text-xs text-fg-subtle">
                          {es.notifications.utilizationThreshold}
                          <input
                            type="text"
                            inputMode="numeric"
                            value={Math.round((rule.thresholdBps ?? spec.thresholdBps) / 100)}
                            disabled={!category.enabled || !rule.enabled}
                            onChange={(e) => {
                              const digits = e.target.value.replace(/\D/g, "").slice(0, 3);
                              update((p) => {
                                p[cat].rules[spec.id].thresholdBps =
                                  Math.max(1, Math.min(100, Number(digits) || 70)) * 100;
                              });
                            }}
                            className="w-14 rounded-lg border border-border-muted bg-surface px-2 py-1 text-center text-xs text-fg disabled:opacity-50"
                          />
                          %
                        </span>
                      )}
                    </div>
                  );
                })}
                {cat === "investments" && (
                  <InvestmentRemindersSection
                    disabled={!category.enabled}
                    // Only rules that are switched on offer their per-investment
                    // frequency; both off hides the whole section.
                    kinds={REMINDER_KINDS.filter(
                      (k) => category.rules[k]?.enabled,
                    )}
                  />
                )}
              </div>
            </section>
          );
        })}
      </div>
    </div>
  );
}

const REMINDER_KINDS: ReminderKind[] = ["contribute", "performance"];
const CADENCES: ReminderCadence[] = ["daily", "weekly", "biweekly", "monthly"];

/** Per-investment reminder frequencies ("remind me to contribute every X" /
 *  "summarize returns every X"), configured here and nowhere else. Only the
 *  kinds whose rule is enabled are shown. */
function InvestmentRemindersSection({
  disabled,
  kinds,
}: {
  disabled: boolean;
  kinds: ReminderKind[];
}) {
  const queryClient = useQueryClient();
  const investments = useQuery({
    queryKey: ["investments", {}],
    queryFn: () => listInvestments(),
  });
  const reminders = useQuery({
    queryKey: ["investmentReminders"],
    queryFn: listInvestmentReminders,
  });
  const save = useMutation({
    mutationFn: (a: { id: number; kind: ReminderKind; cadence: ReminderCadence | null }) =>
      setInvestmentReminder(a.id, a.kind, a.cadence),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["investmentReminders"] }),
  });

  const open = (investments.data ?? []).filter((i) => !i.isClosed);
  if (open.length === 0 || kinds.length === 0) return null;
  const byKey = new Map(
    (reminders.data ?? []).map((r) => [`${r.investmentId}:${r.kind}`, r.cadence]),
  );
  const cadenceLabels = es.notifications.reminders.cadences as Record<string, string>;
  const kindLabels: Record<ReminderKind, string> = {
    contribute: es.notifications.reminders.contribute,
    performance: es.notifications.reminders.performance,
  };

  return (
    <div className="mt-1 border-t border-border-muted pt-3">
      <p className="text-xs font-medium text-fg-muted">{es.notifications.perInvestment}</p>
      <p className="mt-0.5 mb-2 text-xs text-fg-subtle">
        {es.notifications.perInvestmentHint}
      </p>
      <div className="flex flex-col gap-2">
        {open.map((inv) => (
          <div key={inv.id} className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 flex-1 truncate text-sm text-fg-muted">{inv.name}</span>
            {kinds.map((kind) => (
              <label key={kind} className="flex items-center gap-1.5 text-xs text-fg-subtle">
                {kindLabels[kind]}
                <select
                  value={byKey.get(`${inv.id}:${kind}`) ?? ""}
                  disabled={disabled || save.isPending || reminders.isPending}
                  onChange={(e) =>
                    save.mutate({
                      id: inv.id,
                      kind,
                      cadence: (e.target.value || null) as ReminderCadence | null,
                    })
                  }
                  className="rounded-lg border border-border-muted bg-surface px-2 py-1 text-xs text-fg disabled:opacity-50"
                >
                  <option value="">{es.notifications.reminders.off}</option>
                  {CADENCES.map((c) => (
                    <option key={c} value={c}>
                      {cadenceLabels[c]}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
