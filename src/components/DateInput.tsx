import {
  addMonths,
  endOfMonth,
  format,
  getDate,
  getDaysInMonth,
  parseISO,
  startOfMonth,
} from "date-fns";
import { es as esLocale } from "date-fns/locale";
import { Calendar, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { inputClass } from "./Field";
import { es } from "../i18n/es";

const WEEKDAYS = ["L", "M", "M", "J", "V", "S", "D"];
const MONTHS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
const YEAR_FROM = 1970;

interface DateInputProps {
  /** ISO date 'YYYY-MM-DD' */
  value: string;
  onChange: (value: string) => void;
  /** Earliest selectable ISO date (inclusive). */
  min?: string;
}

/** Themed calendar picker. The native <input type="date"> popup in WebKitGTK
 *  grabs input focus and freezes the window until it loses focus, so dates
 *  are picked with our own popover instead. */
export function DateInput({ value, onChange, min }: DateInputProps) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<"days" | "months">("days");
  const selected = value ? parseISO(value) : new Date();
  const [view, setView] = useState(startOfMonth(selected));
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  // Monday-first column of the 1st of the viewed month.
  const leadingBlanks = (view.getDay() + 6) % 7;
  const daysInMonth = getDaysInMonth(view);
  const todayIso = format(new Date(), "yyyy-MM-dd");

  function isoFor(day: number): string {
    return format(
      new Date(view.getFullYear(), view.getMonth(), day),
      "yyyy-MM-dd",
    );
  }

  function pick(day: number) {
    onChange(isoFor(day));
    setOpen(false);
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        className={`${inputClass} flex items-center justify-between gap-2 text-left`}
        onClick={() => {
          setView(startOfMonth(value ? parseISO(value) : new Date()));
          setMode("days");
          setOpen((o) => !o);
        }}
      >
        <span>
          {value
            ? format(parseISO(value), "d 'de' MMMM yyyy", { locale: esLocale })
            : es.common.pickDate}
        </span>
        <Calendar size={15} className="shrink-0 text-stone-500" />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-2 w-72 rounded-xl border border-border-muted bg-surface-overlay p-3 shadow-2xl">
          {mode === "months" ? (
            <>
              <div className="mb-3 flex items-center justify-center gap-2">
                <button
                  type="button"
                  onClick={() => setView((v) => addMonths(v, -12))}
                  className="rounded-md p-1.5 text-stone-400 hover:bg-surface-raised hover:text-stone-200"
                >
                  <ChevronLeft size={16} />
                </button>
                <select
                  className="rounded-lg border border-border-muted bg-surface py-1 pl-2 text-sm font-medium text-stone-100 outline-none"
                  value={view.getFullYear()}
                  onChange={(e) =>
                    setView(new Date(Number(e.target.value), view.getMonth(), 1))
                  }
                >
                  {Array.from(
                    { length: new Date().getFullYear() + 2 - YEAR_FROM },
                    (_, i) => YEAR_FROM + i,
                  )
                    .reverse()
                    .map((y) => (
                      <option key={y} value={y}>
                        {y}
                      </option>
                    ))}
                </select>
                <button
                  type="button"
                  onClick={() => setView((v) => addMonths(v, 12))}
                  className="rounded-md p-1.5 text-stone-400 hover:bg-surface-raised hover:text-stone-200"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
              <div className="grid grid-cols-3 gap-1">
                {MONTHS.map((m, i) => {
                  const isCurrent =
                    i === view.getMonth() &&
                    view.getFullYear() === (value ? parseISO(value) : new Date()).getFullYear();
                  // a month is selectable if its last day is not before min
                  const lastDay = format(
                    endOfMonth(new Date(view.getFullYear(), i, 1)),
                    "yyyy-MM-dd",
                  );
                  const disabled = min !== undefined && lastDay < min;
                  return (
                    <button
                      key={m}
                      type="button"
                      disabled={disabled}
                      onClick={() => {
                        setView(new Date(view.getFullYear(), i, 1));
                        setMode("days");
                      }}
                      className={`rounded-lg py-2 text-sm capitalize transition-colors ${
                        disabled
                          ? "cursor-not-allowed text-stone-700"
                          : isCurrent
                            ? "bg-accent-dim/15 font-semibold text-accent hover:bg-surface-raised"
                            : "text-stone-300 hover:bg-surface-raised"
                      }`}
                    >
                      {m}
                    </button>
                  );
                })}
              </div>
            </>
          ) : (
          <>
          <div className="mb-2 flex items-center justify-between">
            <button
              type="button"
              onClick={() => setView((v) => addMonths(v, -1))}
              className="rounded-md p-1.5 text-stone-400 hover:bg-surface-raised hover:text-stone-200"
            >
              <ChevronLeft size={16} />
            </button>
            <button
              type="button"
              onClick={() => setMode("months")}
              className="rounded-lg px-2 py-1 text-sm font-medium capitalize hover:bg-surface-raised"
              title={es.common.pickMonthYear}
            >
              {format(view, "MMMM yyyy", { locale: esLocale })} ▾
            </button>
            <button
              type="button"
              onClick={() => setView((v) => addMonths(v, 1))}
              className="rounded-md p-1.5 text-stone-400 hover:bg-surface-raised hover:text-stone-200"
            >
              <ChevronRight size={16} />
            </button>
          </div>

          <div className="grid grid-cols-7 gap-1 text-center">
            {WEEKDAYS.map((d, i) => (
              <span key={i} className="py-1 text-xs font-medium text-stone-500">
                {d}
              </span>
            ))}
            {Array.from({ length: leadingBlanks }).map((_, i) => (
              <span key={`b${i}`} />
            ))}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const day = i + 1;
              const iso = isoFor(day);
              const disabled = min !== undefined && iso < min;
              const isSelected = iso === value;
              const isToday = iso === todayIso;
              return (
                <button
                  key={day}
                  type="button"
                  disabled={disabled}
                  onClick={() => pick(day)}
                  className={`rounded-lg py-1.5 text-sm tabular-nums transition-colors ${
                    isSelected
                      ? "bg-accent-dim font-semibold text-surface"
                      : disabled
                        ? "cursor-not-allowed text-stone-700"
                        : isToday
                          ? "font-semibold text-accent hover:bg-surface-raised"
                          : "text-stone-300 hover:bg-surface-raised"
                  }`}
                >
                  {day}
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => {
              if (min === undefined || todayIso >= min) {
                onChange(todayIso);
                setOpen(false);
              } else {
                setView(startOfMonth(endOfMonth(parseISO(min))));
              }
            }}
            className="mt-2 w-full rounded-lg py-1.5 text-center text-sm text-accent hover:bg-surface-raised"
          >
            {es.common.today} · {getDate(new Date())} {format(new Date(), "MMM", { locale: esLocale })}
          </button>
          </>
          )}
        </div>
      )}
    </div>
  );
}
