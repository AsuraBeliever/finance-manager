// Turns a stored notification (kind = i18n key + raw params) into display
// text in the active locale. Money and dates are formatted here — the server
// never stores rendered text, so alerts follow the language setting.
import { es } from "../../i18n/es";
import { formatCents } from "../../lib/money";
import { formatDayMonth } from "../../lib/date";

type Params = Record<string, unknown>;

function daysPhrase(days: number): string {
  if (days <= 0) return es.notifications.today;
  if (days === 1) return es.notifications.tomorrow;
  return es.notifications.inDays.replace("{n}", String(days));
}

/** Category prefix of a kind ('credit.dueSoon' → 'credit') for icon/color. */
export function notificationCategory(kind: string): string {
  return kind.split(".")[0] ?? "";
}

export function renderNotification(kind: string, paramsJson: string): string {
  let params: Params = {};
  try {
    params = JSON.parse(paramsJson) as Params;
  } catch {
    // corrupt params → generic text below
  }

  // First performance summary has no previous value to diff against.
  const key =
    kind === "inv.performance" && params.gainSinceCents == null
      ? "inv.performanceFirst"
      : kind;
  const template = (es.notificationKinds as Record<string, string>)[key];
  if (!template) return es.notifications.generic;

  const currency = typeof params.currencyCode === "string" ? params.currencyCode : "MXN";
  const values: Record<string, string> = {};
  for (const [k, v] of Object.entries(params)) {
    if (v == null) continue;
    if (k.endsWith("Cents") && typeof v === "number") {
      // amountCents → {amount}, totalGainCents → {totalGain}, …
      values[k.slice(0, -"Cents".length)] = formatCents(v, currency);
    } else if ((k === "date" || k === "since") && typeof v === "string") {
      values[k] = formatDayMonth(v);
    } else {
      values[k] = String(v);
    }
  }
  if (typeof params.days === "number") values.when = daysPhrase(params.days);
  if (typeof params.utilizationBps === "number") {
    values.utilization = `${Math.round(params.utilizationBps / 100)} %`;
  }
  if (typeof params.cadence === "string") {
    const words = es.notifications.periodWords as Record<string, string>;
    values.period = words[params.cadence] ?? params.cadence;
  }

  return template.replace(/\{(\w+)\}/g, (_, name: string) => values[name] ?? "");
}
