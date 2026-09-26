// Account-bound preferences: language, clock, timezone, theme and the "What's
// new" toggle follow the account, so a new device, a reinstall or a wiped
// browser restores them instead of starting from the device defaults.
//
// Stored as one envelope in the `preferences` account setting, last-write-wins
// by `updatedAt` — the rule appearance.ts already uses. Android reads and writes
// the same envelope (android/.../data/AppearanceSync.kt); keep the two in step.
//
// Only preferences someone actually chose travel: a field this device never set
// is left out, so a fresh device can't overwrite the account with its defaults.
import { getSetting, setSetting } from "./api";
import { getLocale, setLocale, type Locale } from "../i18n/store";
import { getClock, setClock, type Clock } from "./timeFormat";
import { getTimezone, setTimezone } from "./timezone";
import { getThemePref, setThemePref, type ThemePref } from "./theme";
import { changelogEnabled, setChangelogEnabled } from "./changelog";
import { onPrefChange } from "./prefsBus";

const SETTING_KEY = "preferences";
// Per user: another account signing in on this browser must not look "newer"
// than its own copy just because the previous one edited something here.
const STAMP_PREFIX = "finanzas.prefs.updatedAt.";
let userId: number | null = null;

export interface PrefsEnvelope {
  updatedAt: number;
  locale?: Locale;
  clock?: Clock;
  timezone?: string;
  theme?: ThemePref;
  changelogEnabled?: boolean;
}

type Fields = Omit<PrefsEnvelope, "updatedAt">;

function stored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function localStamp(): number {
  return userId === null ? 0 : Number(stored(STAMP_PREFIX + userId)) || 0;
}

function setLocalStamp(stamp: number) {
  if (userId === null) return;
  try {
    localStorage.setItem(STAMP_PREFIX + userId, String(stamp));
  } catch {
    /* ignore */
  }
}

/** The preferences this device holds an explicit choice for. */
function explicitFields(): Fields {
  const f: Fields = {};
  if (stored("finanzas.locale")) f.locale = getLocale();
  if (stored("finanzas.clock")) f.clock = getClock();
  if (stored("finanzas.timezone")) f.timezone = getTimezone();
  if (stored("finanzas.theme")) f.theme = getThemePref();
  if (stored("finanzas.changelog.enabled")) f.changelogEnabled = changelogEnabled();
  return f;
}

// Applying the account's copy goes through the same setters a click does; this
// keeps those writes from bouncing straight back to the server.
let applying = false;

function apply(remote: PrefsEnvelope) {
  applying = true;
  try {
    if (remote.locale === "es" || remote.locale === "en") setLocale(remote.locale);
    if (remote.clock === "12" || remote.clock === "24") setClock(remote.clock);
    if (typeof remote.timezone === "string" && remote.timezone) setTimezone(remote.timezone);
    if (remote.theme === "light" || remote.theme === "dark" || remote.theme === "system") {
      setThemePref(remote.theme);
    }
    if (typeof remote.changelogEnabled === "boolean") setChangelogEnabled(remote.changelogEnabled);
  } finally {
    applying = false;
  }
}

async function push(): Promise<void> {
  if (userId === null) return;
  const fields = explicitFields();
  if (Object.keys(fields).length === 0) return;
  const stamp = Date.now();
  setLocalStamp(stamp);
  await setSetting(SETTING_KEY, JSON.stringify({ updatedAt: stamp, ...fields }));
}

/** On sign-in / app start: adopt the account's preferences when they are the
 *  newer copy, and hand the account whatever this device has that it lacks. */
export async function hydratePreferencesFromServer(uid: number): Promise<void> {
  userId = uid;
  let remote: PrefsEnvelope | null = null;
  try {
    const raw = await getSetting(SETTING_KEY);
    if (raw) remote = JSON.parse(raw) as PrefsEnvelope;
  } catch {
    return; // offline or signed out: can't tell who is newer, so touch nothing
  }
  const mine = localStamp();
  if (remote && remote.updatedAt >= mine) {
    apply(remote);
    setLocalStamp(remote.updatedAt);
  }
  const local = explicitFields();
  const missing = (Object.keys(local) as (keyof Fields)[]).some(
    (k) => remote?.[k] === undefined,
  );
  if (!remote || mine > remote.updatedAt || missing) await push().catch(() => {});
}

// A choice made in Settings goes to the account right away (best-effort: if it
// fails, the newer local stamp makes the next hydrate push it).
onPrefChange(() => {
  if (applying || userId === null) return;
  setLocalStamp(Date.now());
  push().catch(() => {});
});
