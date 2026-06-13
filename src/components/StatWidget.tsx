import type { ReactNode } from "react";

interface StatWidgetProps {
  title: string;
  /** Optional right-aligned control (a link, a filter, a button). */
  action?: ReactNode;
  className?: string;
  children: ReactNode;
}

/** Dashboard card: a serif heading with an optional action, over content. */
export function StatWidget({ title, action, className = "", children }: StatWidgetProps) {
  return (
    <section
      className={`rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card ${className}`}
    >
      <header className="mb-4 flex items-center justify-between gap-3">
        <h3 className="font-display text-lg font-medium tracking-tight text-fg">{title}</h3>
        {action}
      </header>
      {children}
    </section>
  );
}
