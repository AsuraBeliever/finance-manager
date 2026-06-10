import type { ReactNode } from "react";

interface FieldProps {
  label: string;
  children: ReactNode;
}

/** Form row: label above control, consistent spacing. */
export function Field({ label, children }: FieldProps) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm text-zinc-400">{label}</span>
      {children}
    </label>
  );
}

export const inputClass =
  "w-full rounded-lg border border-border-muted bg-surface px-3 py-2 text-sm " +
  "text-zinc-100 outline-none transition-colors focus:border-accent-dim";
