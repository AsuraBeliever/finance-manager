import { Monitor, Moon, Sun } from "lucide-react";
import { useThemePref, type ThemePref } from "../lib/theme";
import { es } from "../i18n/es";

const OPTIONS: { value: ThemePref; icon: typeof Sun; label: string }[] = [
  { value: "light", icon: Sun, label: es.theme.light },
  { value: "dark", icon: Moon, label: es.theme.dark },
  { value: "system", icon: Monitor, label: es.theme.system },
];

/** Segmented light / dark / auto control. Used in Settings and the sidebar. */
export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const [pref, setPref] = useThemePref();
  return (
    <div
      role="radiogroup"
      aria-label={es.theme.label}
      className="inline-flex items-center gap-1 rounded-lg border border-border-muted bg-surface p-1"
    >
      {OPTIONS.map(({ value, icon: Icon, label }) => {
        const active = pref === value;
        return (
          <button
            key={value}
            role="radio"
            aria-checked={active}
            title={label}
            onClick={() => setPref(value)}
            className={`flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${
              active
                ? "bg-accent-dim/20 text-accent"
                : "text-fg-muted hover:bg-surface-overlay hover:text-fg"
            }`}
          >
            <Icon size={15} />
            {!compact && label}
          </button>
        );
      })}
    </div>
  );
}
