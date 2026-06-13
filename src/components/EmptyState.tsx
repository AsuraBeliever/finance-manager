import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-border-muted bg-surface-raised/40 py-16 text-center">
      <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-accent-dim/15 text-accent ring-1 ring-accent/20">
        <Icon size={24} />
      </span>
      <p className="font-display text-lg font-medium text-stone-100">{title}</p>
      {description && <p className="max-w-sm text-sm text-stone-500">{description}</p>}
      {action}
    </div>
  );
}
