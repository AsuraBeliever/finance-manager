import { Check, TrendingUp } from "lucide-react";
import { es } from "../i18n/es";

/** The «Rendimientos» switch: on lists what the money earned by itself (the
 *  investments' daily yield, the wallets' automatic interest), off hides it.
 *  Shared by the transactions filters and the investment detail's table. */
export function YieldToggle({ on, onChange }: { on: boolean; onChange: (on: boolean) => void }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={() => onChange(!on)}
      className={`flex items-center gap-2 self-start rounded-lg border px-3 py-1.5 text-sm transition-colors ${
        on
          ? "border-accent/40 bg-accent/15 text-fg"
          : "border-border-muted bg-surface text-fg-subtle hover:border-accent"
      }`}
    >
      {on ? <Check size={15} className="text-accent" /> : <TrendingUp size={15} />}
      {es.transactions.yieldToggle}
    </button>
  );
}
