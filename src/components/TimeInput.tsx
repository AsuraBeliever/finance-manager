import { useState } from "react";
import { Clock } from "lucide-react";
import { inputClass } from "./Field";
import { useClock, type Clock as ClockFmt } from "../lib/timeFormat";

interface TimeInputProps {
  /** 'HH:MM' (24h), or "" for no time. */
  value: string;
  onChange: (value: string) => void;
}

type Meridiem = "AM" | "PM";

/** Split a stored 'HH:MM' (24h) into what the box shows for the given clock. */
function toDraft(value: string, clock: ClockFmt): { text: string; meridiem: Meridiem } {
  const [h, m] = value.split(":");
  const hour = Number(h);
  const min = Number(m);
  if (value === "" || Number.isNaN(hour) || Number.isNaN(min)) {
    return { text: "", meridiem: "AM" };
  }
  const mm = String(min).padStart(2, "0");
  if (clock === "24") return { text: `${String(hour).padStart(2, "0")}:${mm}`, meridiem: "AM" };
  const h12 = ((hour + 11) % 12) + 1;
  return { text: `${h12}:${mm}`, meridiem: hour < 12 ? "AM" : "PM" };
}

/** Parse whatever the user typed into a 24h 'HH:MM' (or "" when blank). When a
 *  colon is present it anchors the split: left digits are the hour, right the
 *  minute — each capped at its own two digits (so "1:560" → 01:56, not 12:59).
 *  Without a colon, the last two digits are the minute. */
function parse(text: string, clock: ClockFmt, meridiem: Meridiem): string {
  let hourDigits: string;
  let minDigits: string;
  if (text.includes(":")) {
    const [left = "", right = ""] = text.split(":");
    hourDigits = left.replace(/\D/g, "");
    minDigits = right.replace(/\D/g, "");
  } else {
    const digits = text.replace(/\D/g, "").slice(0, 4);
    if (digits.length <= 2) {
      hourDigits = digits;
      minDigits = "";
    } else {
      hourDigits = digits.slice(0, digits.length - 2);
      minDigits = digits.slice(digits.length - 2);
    }
  }
  if (hourDigits === "" && minDigits === "") return "";
  let hour = Number(hourDigits.slice(0, 2) || "0");
  let min = Math.min(59, Number(minDigits.slice(0, 2) || "0"));
  if (clock === "24") {
    hour = Math.min(23, hour);
  } else {
    hour = Math.min(12, Math.max(1, hour)) % 12; // 12 → 0, 1-11 stay
    if (meridiem === "PM") hour += 12;
  }
  return `${String(hour).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
}

/** Typed HH:MM entry (no dropdown). Native <input type="time"> is avoided
 *  because its popup freezes WebKitGTK — same reason DateInput rolls its own.
 *  The box respects the chosen clock: 24h edits 'HH:MM'; 12h edits 'h:MM' with
 *  an AM/PM toggle. Storage is always 24h 'HH:MM'. */
export function TimeInput({ value, onChange }: TimeInputProps) {
  const clock = useClock();
  const [focused, setFocused] = useState(false);
  const [text, setText] = useState("");

  const draft = toDraft(value, clock);
  const meridiem = draft.meridiem;
  // Show the user's live keystrokes while focused, otherwise the stored value.
  const shown = focused ? text : draft.text;

  const commit = (nextText: string, nextMeridiem: Meridiem) => {
    onChange(parse(nextText, clock, nextMeridiem));
  };

  return (
    <div className={`${inputClass} flex items-center gap-1.5`}>
      <Clock size={15} className="shrink-0 text-fg-subtle" />
      <input
        type="text"
        inputMode="numeric"
        aria-label="Hora"
        placeholder={clock === "24" ? "--:--" : "-:--"}
        maxLength={5}
        className="min-w-0 flex-1 bg-transparent tabular-nums outline-none placeholder:text-fg-subtle"
        value={shown}
        onFocus={() => {
          setText(draft.text);
          setFocused(true);
        }}
        onChange={(e) => {
          const next = e.target.value.replace(/[^\d:]/g, "");
          setText(next);
          commit(next, meridiem);
        }}
        onBlur={() => {
          setFocused(false);
          // Normalize the displayed text to the canonical form.
          commit(text, meridiem);
        }}
      />
      {clock === "12" && (
        <button
          type="button"
          aria-label="AM/PM"
          className="shrink-0 rounded-md border border-border bg-surface px-2 py-0.5 text-xs font-semibold tabular-nums text-fg shadow-sm transition-colors hover:bg-surface-overlay active:bg-surface-muted"
          onClick={() => {
            const next: Meridiem = meridiem === "AM" ? "PM" : "AM";
            if (value !== "") commit(focused ? text : draft.text, next);
          }}
        >
          {meridiem}
        </button>
      )}
    </div>
  );
}
