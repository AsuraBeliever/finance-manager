import { useCallback, useState } from "react";
import type { Period } from "../../lib/types";

/** The global dashboard period (hero + breakdowns + both flow charts). */
export const DASHBOARD_PERIOD_KEY = "finanzas.dashboard.period";

function load(key: string, fallback: Period): Period {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw) as Period;
  } catch {
    /* ignore */
  }
  return fallback;
}

/** A Period remembered across sessions in localStorage, keyed so several charts
 *  can each keep their own selection. */
export function usePeriod(
  key: string = DASHBOARD_PERIOD_KEY,
  initial: Period = { kind: "currentMonth" },
): [Period, (p: Period) => void] {
  const [period, setPeriod] = useState<Period>(() => load(key, initial));
  const update = useCallback(
    (p: Period) => {
      setPeriod(p);
      try {
        localStorage.setItem(key, JSON.stringify(p));
      } catch {
        /* ignore */
      }
    },
    [key],
  );
  return [period, update];
}
