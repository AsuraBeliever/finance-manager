import { Palette } from "lucide-react";
import { CATEGORY_PALETTE } from "../lib/palette";
import { es } from "../i18n/es";

/** The single, shared color picker: a row of preset swatches plus a custom
 *  swatch that opens the OS color palette. Used everywhere a color is chosen
 *  (categories, subscriptions, goals…). `value` is a hex string or null (none
 *  selected yet); `onChange` always returns a hex string. */
export function ColorPicker({
  value,
  onChange,
}: {
  value: string | null;
  onChange: (color: string) => void;
}) {
  const isPreset = value != null && CATEGORY_PALETTE.includes(value);
  const customActive = value != null && !isPreset;
  const ring = "ring-2 ring-accent ring-offset-2 ring-offset-surface-raised";

  return (
    <div className="flex flex-wrap items-center gap-2">
      {CATEGORY_PALETTE.map((c) => (
        <button
          key={c}
          type="button"
          aria-label={c}
          onClick={() => onChange(c)}
          style={{ backgroundColor: c }}
          className={`h-6 w-6 rounded-full transition-transform hover:scale-110 ${value === c ? ring : ""}`}
        />
      ))}
      {/* Custom color via the OS palette — the fallback when no preset fits. */}
      <label
        title={es.categories.customColor}
        className={`relative flex h-6 w-6 cursor-pointer items-center justify-center rounded-full ring-1 ring-inset ring-white/25 transition-transform hover:scale-110 ${customActive ? ring : ""}`}
        style={{
          background: customActive
            ? (value as string)
            : "conic-gradient(from 0deg,#ef4444,#eab308,#22c55e,#06b6d4,#3b82f6,#a855f7,#ef4444)",
        }}
      >
        <Palette size={12} className="text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.6)]" />
        <input
          type="color"
          value={customActive ? (value as string) : "#a855f7"}
          onChange={(e) => onChange(e.target.value)}
          className="absolute inset-0 cursor-pointer opacity-0"
        />
      </label>
    </div>
  );
}
