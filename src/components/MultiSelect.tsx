import { Check, ChevronDown } from "lucide-react";
import { createPortal } from "react-dom";
import { inputClass } from "./Field";
import type { SelectOption } from "./Select";
import { useAnchoredPopover } from "./Select";

interface MultiSelectProps<T extends string | number> {
  /** Picked values; empty means "all", which `allLabel` stands for. */
  values: T[];
  onChange: (values: T[]) => void;
  options: SelectOption<T>[];
  allLabel: string;
  /** Trigger text for two or more picks; `{n}` is replaced by the count. */
  manyLabel: string;
  className?: string;
  "aria-label"?: string;
}

/** `Select` with checkboxes: the list stays open while you tick several
 *  options, and the first row ("all") clears the selection. */
export function MultiSelect<T extends string | number>({
  values,
  onChange,
  options,
  allLabel,
  manyLabel,
  className = "",
  "aria-label": ariaLabel,
}: MultiSelectProps<T>) {
  const { open, setOpen, btnRef, popRef, pos } = useAnchoredPopover();
  const picked = options.filter((o) => values.includes(o.value));
  const label =
    picked.length === 0
      ? allLabel
      : picked.length === 1
        ? picked[0].label
        : manyLabel.replace("{n}", String(picked.length));

  const toggle = (v: T) =>
    onChange(values.includes(v) ? values.filter((x) => x !== v) : [...values, v]);

  const row = (key: string, text: string, active: boolean, onClick: () => void) => (
    <button
      key={key}
      type="button"
      role="menuitemcheckbox"
      aria-checked={active}
      onClick={onClick}
      className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors ${
        active ? "bg-surface-raised text-fg" : "text-fg-muted hover:bg-surface-raised"
      }`}
    >
      <span className="flex h-4 w-4 shrink-0 items-center justify-center">
        {active && <Check size={14} className="text-accent" />}
      </span>
      <span className="truncate">{text}</span>
    </button>
  );

  return (
    <>
      <button
        ref={btnRef}
        type="button"
        aria-label={ariaLabel}
        className={`${inputClass} flex items-center justify-between gap-2 text-left ${className}`}
        onClick={() => setOpen((o) => !o)}
      >
        <span className="truncate">{label}</span>
        <ChevronDown size={15} className="shrink-0 text-fg-subtle" />
      </button>

      {open &&
        createPortal(
          <div
            ref={popRef}
            data-select-portal
            style={{ position: "fixed", top: pos.top, left: pos.left, width: pos.width }}
            className="z-[70] max-h-64 min-w-[7rem] overflow-y-auto rounded-xl border border-border-muted bg-surface-overlay p-1 shadow-2xl"
          >
            {row("all", allLabel, values.length === 0, () => onChange([]))}
            {options.map((o) =>
              row(String(o.value), o.label, values.includes(o.value), () => toggle(o.value)),
            )}
          </div>,
          document.body,
        )}
    </>
  );
}
