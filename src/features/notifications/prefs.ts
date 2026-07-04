// Notification preferences: the JSON stored in the `notification_prefs`
// setting. Mirrors the worker's serde structs (worker/src/handlers/
// notifications.rs) — anything missing means OFF, so defaults here and
// `#[serde(default)]` there always agree.

export interface ChannelPrefs {
  inApp: boolean;
  /** Stored for the future email channel; nothing sends it yet. */
  email: boolean;
}

export interface RulePrefs {
  enabled: boolean;
  daysBefore?: number;
  thresholdBps?: number;
  channels: ChannelPrefs;
}

export interface CategoryPrefs {
  enabled: boolean;
  rules: Record<string, RulePrefs>;
}

export type CategoryId = "credit" | "goals" | "subscriptions" | "investments";

export interface NotificationPrefs {
  /** Master switch for the email channel (digest to the account's address). */
  emailEnabled: boolean;
  credit: CategoryPrefs;
  goals: CategoryPrefs;
  subscriptions: CategoryPrefs;
  investments: CategoryPrefs;
}

export const PREFS_KEY = "notification_prefs";

interface RuleSpec {
  id: string;
  daysBefore?: number;
  thresholdBps?: number;
}

/** Every rule per category, with its default params. Order = display order. */
export const RULE_CATALOG: Record<CategoryId, RuleSpec[]> = {
  credit: [
    { id: "cutSoon", daysBefore: 3 },
    { id: "dueSoon", daysBefore: 5 },
    { id: "utilization", thresholdBps: 7000 },
    { id: "anniversary", daysBefore: 14 },
    { id: "msiPosted" },
  ],
  goals: [
    { id: "contribution" },
    { id: "behind" },
    { id: "deadlineSoon", daysBefore: 7 },
    { id: "completed" },
  ],
  subscriptions: [{ id: "chargeSoon", daysBefore: 3 }, { id: "chargeToday" }],
  investments: [
    { id: "contribute" },
    { id: "performance" },
    { id: "cetesMaturity", daysBefore: 7 },
  ],
};

export const CATEGORY_IDS = Object.keys(RULE_CATALOG) as CategoryId[];

function defaultRule(spec: RuleSpec): RulePrefs {
  return {
    enabled: false,
    ...(spec.daysBefore !== undefined && { daysBefore: spec.daysBefore }),
    ...(spec.thresholdBps !== undefined && { thresholdBps: spec.thresholdBps }),
    channels: { inApp: true, email: false },
  };
}

export function defaultPrefs(): NotificationPrefs {
  const out = { emailEnabled: false } as NotificationPrefs;
  for (const cat of CATEGORY_IDS) {
    const rules: Record<string, RulePrefs> = {};
    for (const spec of RULE_CATALOG[cat]) rules[spec.id] = defaultRule(spec);
    out[cat] = { enabled: false, rules };
  }
  return out;
}

/** Saved JSON → full prefs: every cataloged rule present, unknown ones kept. */
export function mergePrefs(json: string | null): NotificationPrefs {
  const base = defaultPrefs();
  if (!json) return base;
  let saved: Partial<NotificationPrefs>;
  try {
    saved = JSON.parse(json) as Partial<NotificationPrefs>;
  } catch {
    return base;
  }
  base.emailEnabled = saved.emailEnabled === true;
  for (const cat of CATEGORY_IDS) {
    const s = saved[cat];
    if (!s) continue;
    base[cat].enabled = s.enabled === true;
    for (const [id, rule] of Object.entries(s.rules ?? {})) {
      base[cat].rules[id] = {
        ...base[cat].rules[id],
        ...rule,
        channels: { ...base[cat].rules[id]?.channels, ...rule.channels },
      };
    }
  }
  return base;
}
