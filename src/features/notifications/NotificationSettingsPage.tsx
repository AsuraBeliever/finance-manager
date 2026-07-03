import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { PageHeader } from "../../components/PageHeader";
import { getSetting, setSetting } from "../../lib/api";
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
      <>
        <PageHeader
          title={es.notifications.title}
          backTo="/ajustes"
          backLabel={es.settings.back}
        />
        <p className="text-sm text-fg-subtle">{es.common.loading}</p>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={es.notifications.title}
        backTo="/ajustes"
        backLabel={es.settings.back}
      />
      <p className="mb-6 max-w-xl text-sm text-fg-muted">{es.notifications.pageHint}</p>

      <div className="grid max-w-3xl gap-4 lg:grid-cols-2">
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
                            type="number"
                            min={0}
                            max={60}
                            value={rule.daysBefore ?? spec.daysBefore}
                            disabled={!category.enabled || !rule.enabled}
                            onChange={(e) =>
                              update((p) => {
                                p[cat].rules[spec.id].daysBefore = Math.max(
                                  0,
                                  Math.min(60, Number(e.target.value) || 0),
                                );
                              })
                            }
                            className="w-14 rounded-lg border border-border-muted bg-surface px-2 py-1 text-center text-xs text-fg disabled:opacity-50"
                          />
                          {es.notifications.daysBefore}
                        </span>
                      )}
                      {spec.thresholdBps !== undefined && (
                        <span className="flex items-center gap-1.5 text-xs text-fg-subtle">
                          {es.notifications.utilizationThreshold}
                          <input
                            type="number"
                            min={1}
                            max={100}
                            value={Math.round((rule.thresholdBps ?? spec.thresholdBps) / 100)}
                            disabled={!category.enabled || !rule.enabled}
                            onChange={(e) =>
                              update((p) => {
                                p[cat].rules[spec.id].thresholdBps =
                                  Math.max(1, Math.min(100, Number(e.target.value) || 70)) * 100;
                              })
                            }
                            className="w-14 rounded-lg border border-border-muted bg-surface px-2 py-1 text-center text-xs text-fg disabled:opacity-50"
                          />
                          %
                        </span>
                      )}
                    </div>
                  );
                })}
                {cat === "investments" && (
                  <p className="text-xs leading-relaxed text-fg-subtle">
                    {es.notifications.invRulesHint}{" "}
                    <Link to="/inversiones" className="text-accent hover:underline">
                      {es.nav.investments}
                    </Link>
                  </p>
                )}
              </div>
            </section>
          );
        })}
      </div>
    </>
  );
}
