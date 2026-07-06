import { Clock } from "lucide-react";
import { inputClass } from "./Field";

interface TimeInputProps {
  /** 'HH:MM' (24h), or "" for no time. */
  value: string;
  onChange: (value: string) => void;
}

const HOURS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, "0"));
const MINUTES = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, "0"));

/** Hour/minute pair that yields 'HH:MM'. Two native <select>s (which behave in
 *  WebKitGTK, unlike the <input type="time"> popup that freezes the window —
 *  same reason DateInput rolls its own). The 12h display lives in formatTime;
 *  entry stays a clean 24h pick. */
export function TimeInput({ value, onChange }: TimeInputProps) {
  const [h = "", m = ""] = value.split(":");

  return (
    <div className={`${inputClass} flex items-center gap-1.5`}>
      <Clock size={15} className="shrink-0 text-fg-subtle" />
      <select
        aria-label="Hora"
        className="flex-1 bg-transparent tabular-nums outline-none"
        value={h}
        onChange={(e) =>
          onChange(e.target.value === "" ? "" : `${e.target.value}:${m || "00"}`)
        }
      >
        <option value="" className="bg-surface-overlay text-fg">
          --
        </option>
        {HOURS.map((hh) => (
          <option key={hh} value={hh} className="bg-surface-overlay text-fg">
            {hh}
          </option>
        ))}
      </select>
      <span className="text-fg-subtle">:</span>
      <select
        aria-label="Minuto"
        className="flex-1 bg-transparent tabular-nums outline-none"
        value={m}
        onChange={(e) => onChange(`${h || "00"}:${e.target.value}`)}
      >
        {MINUTES.map((mm) => (
          <option key={mm} value={mm} className="bg-surface-overlay text-fg">
            {mm}
          </option>
        ))}
      </select>
    </div>
  );
}
