import { Eye, EyeOff } from "lucide-react";
import { es } from "../i18n/es";
import { useHideBalance } from "../lib/hideBalance";

/** The eye button that flips the app-wide privacy mode: while on, every money
 *  figure is masked. The state is global (localStorage), so one instance
 *  anywhere flips the whole app — it lives in several page headers for reach. */
export function PrivacyToggle({ className = "" }: { className?: string }) {
  const [hidden, toggle] = useHideBalance();
  return (
    <button
      onClick={toggle}
      title={hidden ? es.dashboard.showBalance : es.dashboard.hideBalance}
      aria-pressed={hidden}
      className={`rounded-md p-1 text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg ${className}`}
    >
      {hidden ? <EyeOff size={15} /> : <Eye size={15} />}
    </button>
  );
}
