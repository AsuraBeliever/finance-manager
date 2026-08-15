import { Check, ChevronDown } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { inputClass } from "./Field";

export interface SelectOption<T extends string | number> {
  value: T;
  label: string;
}

interface SelectProps<T extends string | number> {
  value: T;
  onChange: (value: T) => void;
  options: SelectOption<T>[];
  className?: string;
  "aria-label"?: string;
}

/** A themed dropdown that replaces the native <select>. The native popup can
 *  render off-screen on the phone (and inside a transformed/clipped ancestor),
 *  so the list is rendered in a portal at <body> with fixed positioning,
 *  anchored to the trigger, flipped up when there's no room below and always
 *  clamped to the viewport — it can never escape the screen. The portal is
 *  marked `data-select-portal` so an outer dropdown (e.g. the period picker)
 *  doesn't treat a click inside it as "outside" and close itself. */
export function Select<T extends string | number>({
  value,
  onChange,
  options,
  className = "",
  "aria-label": ariaLabel,
}: SelectProps<T>) {
  const [open, setOpen] = useState(false);
  const btnRef = useRef<HTMLButtonElement>(null);
  const popRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number; width: number }>({
    top: 0,
    left: 0,
    width: 0,
  });

  const selected = options.find((o) => o.value === value);

  function reposition() {
    const b = btnRef.current;
    if (!b) return;
    const r = b.getBoundingClientRect();
    const left = Math.max(8, Math.min(r.left, window.innerWidth - r.width - 8));
    const popH = popRef.current?.offsetHeight ?? 240;
    const below = r.bottom + 4;
    const top =
      below + popH > window.innerHeight - 8 && r.top - popH - 8 > 8 ? r.top - popH - 8 : below;
    setPos({ top, left, width: r.width });
  }

  useLayoutEffect(() => {
    if (open) reposition();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (btnRef.current?.contains(t) || popRef.current?.contains(t)) return;
      setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    const onMove = () => reposition();
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    window.addEventListener("resize", onMove);
    window.addEventListener("scroll", onMove, true);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
      window.removeEventListener("resize", onMove);
      window.removeEventListener("scroll", onMove, true);
    };
  }, [open]);

  return (
    <>
      <button
        ref={btnRef}
        type="button"
        aria-label={ariaLabel}
        className={`${inputClass} flex items-center justify-between gap-2 text-left ${className}`}
        onClick={() => setOpen((o) => !o)}
      >
        <span className="truncate">{selected?.label ?? ""}</span>
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
            {options.map((o) => {
              const active = o.value === value;
              return (
                <button
                  key={String(o.value)}
                  type="button"
                  onClick={() => {
                    onChange(o.value);
                    setOpen(false);
                  }}
                  className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors ${
                    active ? "bg-surface-raised text-fg" : "text-fg-muted hover:bg-surface-raised"
                  }`}
                >
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center">
                    {active && <Check size={14} className="text-accent" />}
                  </span>
                  <span className="truncate">{o.label}</span>
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </>
  );
}
