import { format } from "date-fns";
import { CalendarRange, Check, ChevronDown } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { DateInput } from "./DateInput";
import { inputClass } from "./Field";
import { Select } from "./Select";
import { es } from "../i18n/es";
import { getLocale } from "../i18n/store";
import type { Period } from "../lib/types";

const todayIso = () => format(new Date(), "yyyy-MM-dd");
const startOfMonthIso = () => format(new Date(), "yyyy-MM-01");
const intlLocale = () => (getLocale() === "en" ? "en-US" : "es-MX");

const monthName = (year: number, month1: number) =>
  new Intl.DateTimeFormat(intlLocale(), { month: "long", year: "numeric" }).format(
    new Date(year, month1 - 1, 1),
  );

const dayName = (iso: string) =>
  new Intl.DateTimeFormat(intlLocale(), { day: "numeric", month: "long", year: "numeric" }).format(
    new Date(`${iso}T00:00:00`),
  );

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/** Human label for the current selection, shown on the trigger button. */
export function periodLabel(p: Period): string {
  const t = es.dashboard.period;
  switch (p.kind) {
    case "allTime":
      return t.allTime;
    case "currentMonth":
      return t.currentMonth;
    case "lastMonths":
      return t.lastMonthsN.replace("{n}", String(p.months));
    case "month":
      return cap(monthName(p.year, p.month));
    case "day":
      return cap(dayName(p.date));
    case "range":
      return `${dayName(p.from)} – ${dayName(p.to)}`;
  }
}

type Mode = Period["kind"];

const MODES: Mode[] = ["allTime", "currentMonth", "lastMonths", "month", "day", "range"];

function modeLabel(m: Mode): string {
  const t = es.dashboard.period;
  return {
    allTime: t.allTime,
    currentMonth: t.currentMonth,
    lastMonths: t.lastMonths,
    month: t.specificMonth,
    day: t.specificDay,
    range: t.range,
  }[m];
}

/** A sensible default Period when switching into a mode. */
function defaultFor(m: Mode): Period {
  const now = new Date();
  switch (m) {
    case "allTime":
      return { kind: "allTime" };
    case "currentMonth":
      return { kind: "currentMonth" };
    case "lastMonths":
      return { kind: "lastMonths", months: 6 };
    case "month":
      return { kind: "month", year: now.getFullYear(), month: now.getMonth() + 1 };
    case "day":
      return { kind: "day", date: todayIso() };
    case "range":
      return { kind: "range", from: startOfMonthIso(), to: todayIso() };
  }
}

export function PeriodPicker({
  value,
  onChange,
  /** Offer "todo el tiempo" as the first choice. */
  allowAll = false,
}: {
  value: Period;
  onChange: (p: Period) => void;
  allowAll?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const t = es.dashboard.period;

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (rootRef.current?.contains(target)) return;
      // The calendar and the month/year dropdowns render in portals at <body>,
      // so a tap inside them counts as "outside" this dropdown. Without this
      // guard the whole picker would close before the tap registers a choice.
      if (target.closest?.("[data-calendar-portal],[data-select-portal]")) return;
      setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const now = new Date();
  const years = Array.from({ length: 8 }, (_, i) => now.getFullYear() - i);

  return (
    <div ref={rootRef} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-lg border border-border-muted bg-surface px-3 py-1.5 text-sm text-fg transition-colors hover:border-accent"
      >
        <CalendarRange size={15} className="text-fg-subtle" />
        <span className="tabular-nums">{periodLabel(value)}</span>
        <ChevronDown size={14} className="text-fg-subtle" />
      </button>

      {/* On phones the trigger sits at the left edge of the header, so a
          right-anchored panel hangs off screen: anchor it left there and keep
          the tidy right alignment only where the trigger sits on the right. */}
      {open && (
        <div className="absolute left-0 z-30 mt-2 w-72 max-w-[calc(100vw-2rem)] rounded-xl border border-border-muted bg-surface-overlay p-2 shadow-2xl sm:left-auto sm:right-0">
          <ul className="space-y-0.5">
            {MODES.filter((m) => allowAll || m !== "allTime").map((m) => {
              const active = value.kind === m;
              return (
                <li key={m}>
                  <button
                    onClick={() => onChange(active ? value : defaultFor(m))}
                    className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-sm transition-colors ${
                      active ? "bg-surface-overlay text-fg" : "text-fg-muted hover:bg-surface-overlay"
                    }`}
                  >
                    <span className="flex h-4 w-4 shrink-0 items-center justify-center">
                      {active && <Check size={14} className="text-accent" />}
                    </span>
                    {modeLabel(m)}
                  </button>

                  {active && m === "lastMonths" && value.kind === "lastMonths" && (
                    <div className="px-2.5 pb-2 pt-1">
                      <label className="mb-1 block text-xs text-fg-subtle">{t.monthsCount}</label>
                      <input
                        type="number"
                        min={1}
                        max={36}
                        value={value.months}
                        onChange={(e) => {
                          const n = Math.min(36, Math.max(1, Number(e.target.value) || 1));
                          onChange({ kind: "lastMonths", months: n });
                        }}
                        className={inputClass}
                      />
                    </div>
                  )}

                  {active && m === "month" && value.kind === "month" && (
                    <div className="flex gap-2 px-2.5 pb-2 pt-1">
                      <div className="min-w-0 flex-1">
                        <Select
                          aria-label={t.specificMonth}
                          value={value.month}
                          onChange={(month) => onChange({ ...value, month })}
                          options={Array.from({ length: 12 }, (_, i) => i + 1).map((mo) => ({
                            value: mo,
                            label: cap(
                              new Intl.DateTimeFormat(intlLocale(), { month: "long" }).format(
                                new Date(2020, mo - 1, 1),
                              ),
                            ),
                          }))}
                        />
                      </div>
                      <div className="min-w-0 flex-1">
                        <Select
                          aria-label="Año"
                          value={value.year}
                          onChange={(year) => onChange({ ...value, year })}
                          options={years.map((y) => ({ value: y, label: String(y) }))}
                        />
                      </div>
                    </div>
                  )}

                  {active && m === "day" && value.kind === "day" && (
                    <div className="px-2.5 pb-2 pt-1">
                      <DateInput
                        value={value.date}
                        onChange={(date) => onChange({ kind: "day", date })}
                      />
                    </div>
                  )}

                  {active && m === "range" && value.kind === "range" && (
                    <div className="flex items-end gap-2 px-2.5 pb-2 pt-1">
                      <div className="min-w-0 flex-1">
                        <label className="mb-1 block text-xs text-fg-subtle">{t.from}</label>
                        <DateInput
                          value={value.from}
                          onChange={(from) => onChange({ ...value, from })}
                        />
                      </div>
                      <div className="min-w-0 flex-1">
                        <label className="mb-1 block text-xs text-fg-subtle">{t.to}</label>
                        <DateInput
                          value={value.to}
                          min={value.from}
                          onChange={(to) => onChange({ ...value, to })}
                        />
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
