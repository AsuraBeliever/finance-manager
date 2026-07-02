import type { InputHTMLAttributes } from "react";
import { inputClass } from "./Field";

/** Single source of truth for money entry across the app. Shows a `$` adornment
 *  and formats the amount with thousands separators and two decimals, reflowing
 *  on every keystroke (cash-register style: digits fill in from the right, so
 *  it's always a valid, fully formatted amount). The value it stores/returns is
 *  a plain major-unit string like "1234.56" ("" when empty), so callers keep
 *  using `parseToCents` unchanged. Change the look or behavior here and every
 *  money box in the app follows. */
interface MoneyInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type"> {
  /** Amount in major units as a plain string, e.g. "1234.56". "" = empty. */
  value: string;
  onChange: (value: string) => void;
}

/** Cents → "1,234.56" (grouped, always two decimals). */
function groupCents(cents: number): string {
  const s = Math.abs(cents).toString().padStart(3, "0");
  const frac = s.slice(-2);
  const int = s.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${cents < 0 ? "-" : ""}${int}.${frac}`;
}

export function MoneyInput({ value, onChange, placeholder, className, ...rest }: MoneyInputProps) {
  const cents = value === "" ? null : Math.round(parseFloat(value) * 100);
  const display = cents === null || Number.isNaN(cents) ? "" : groupCents(cents);

  function handle(raw: string) {
    // Keep only digits and read them as cents — each keystroke shifts the
    // amount, so "1234" reads as $12.34 and grows as you type.
    const digits = raw.replace(/\D/g, "").slice(0, 13);
    if (digits === "") {
      onChange("");
      return;
    }
    onChange((parseInt(digits, 10) / 100).toFixed(2));
  }

  return (
    <div className="relative">
      <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-fg-subtle">
        $
      </span>
      <input
        {...rest}
        className={`${inputClass} pl-7 tabular-nums ${className ?? ""}`}
        inputMode="numeric"
        placeholder={placeholder ?? "0.00"}
        value={display}
        onChange={(e) => handle(e.target.value)}
      />
    </div>
  );
}
