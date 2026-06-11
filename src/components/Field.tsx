import type { ReactNode } from "react";

interface FieldProps {
  label: string;
  children: ReactNode;
}

/** Form row: label above control, consistent spacing.
 *  Deliberately NOT a <label>: WebKitGTK re-dispatches clicks on a label's
 *  content to its first labelable descendant, which broke composite controls
 *  like DateInput (clicking the month title re-toggled the popover). */
export function Field({ label, children }: FieldProps) {
  return (
    <div>
      <span className="mb-1 block text-sm text-zinc-400">{label}</span>
      {children}
    </div>
  );
}

export const inputClass =
  "w-full rounded-lg border border-border-muted bg-surface px-3 py-2 text-sm " +
  "text-zinc-100 outline-none transition-colors focus:border-accent-dim";
