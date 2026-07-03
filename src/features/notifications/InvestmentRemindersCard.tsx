import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing } from "lucide-react";
import { Link } from "react-router-dom";
import {
  getSetting,
  listInvestmentReminders,
  setInvestmentReminder,
} from "../../lib/api";
import type { ReminderCadence, ReminderKind } from "../../lib/types";
import { es } from "../../i18n/es";
import { mergePrefs, PREFS_KEY } from "./prefs";

const KINDS: ReminderKind[] = ["contribute", "performance"];
const CADENCES: ReminderCadence[] = ["daily", "weekly", "biweekly", "monthly"];

/** Per-investment reminders ("remind me to contribute every X" / "tell me
 *  every X how much it earned"), shown on the investment's detail page. */
export function InvestmentRemindersCard({ investmentId }: { investmentId: number }) {
  const queryClient = useQueryClient();
  const reminders = useQuery({
    queryKey: ["investmentReminders", investmentId],
    queryFn: () => listInvestmentReminders(investmentId),
  });
  const prefs = useQuery({
    queryKey: ["settings", PREFS_KEY],
    queryFn: () => getSetting(PREFS_KEY),
  });

  const save = useMutation({
    mutationFn: ({ kind, cadence }: { kind: ReminderKind; cadence: ReminderCadence | null }) =>
      setInvestmentReminder(investmentId, kind, cadence),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["investmentReminders", investmentId] }),
  });

  const byKind = new Map((reminders.data ?? []).map((r) => [r.kind, r.cadence]));
  const merged = mergePrefs(prefs.data ?? null);
  const globalOff =
    !prefs.isPending &&
    (!merged.investments.enabled ||
      KINDS.every((k) => !merged.investments.rules[k]?.enabled));

  const labels: Record<ReminderKind, string> = {
    contribute: es.notifications.reminders.contribute,
    performance: es.notifications.reminders.performance,
  };
  const cadenceLabels = es.notifications.reminders.cadences as Record<string, string>;

  return (
    <section className="mt-4 rounded-xl border border-border-muted bg-surface-raised p-5">
      <h3 className="mb-1 flex items-center gap-2 font-medium">
        <BellRing size={16} className="text-fg-subtle" />
        {es.notifications.reminders.title}
      </h3>
      <p className="mb-4 text-xs text-fg-subtle">{es.notifications.reminders.hint}</p>
      <div className="flex flex-col gap-3">
        {KINDS.map((kind) => {
          const cadence = byKind.get(kind);
          return (
            <div key={kind} className="flex flex-wrap items-center gap-2">
              <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-sm text-fg-muted">
                <input
                  type="checkbox"
                  checked={cadence !== undefined}
                  disabled={save.isPending || reminders.isPending}
                  onChange={(e) =>
                    save.mutate({ kind, cadence: e.target.checked ? "monthly" : null })
                  }
                  className="accent-accent"
                />
                {labels[kind]}
              </label>
              {cadence !== undefined && (
                <select
                  value={cadence}
                  disabled={save.isPending}
                  onChange={(e) =>
                    save.mutate({ kind, cadence: e.target.value as ReminderCadence })
                  }
                  className="rounded-lg border border-border-muted bg-surface px-2 py-1 text-xs text-fg"
                >
                  {CADENCES.map((c) => (
                    <option key={c} value={c}>
                      {cadenceLabels[c]}
                    </option>
                  ))}
                </select>
              )}
            </div>
          );
        })}
      </div>
      {globalOff && (
        <p className="mt-3 text-xs text-fg-subtle">
          <Link to="/ajustes/notificaciones" className="text-accent hover:underline">
            {es.notifications.reminders.globalOff}
          </Link>
        </p>
      )}
    </section>
  );
}
