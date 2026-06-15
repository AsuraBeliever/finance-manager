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
      className={`group flex h-full flex-col overflow-hidden rounded-2xl border border-border-muted bg-surface-raised p-5 shadow-card transition-colors duration-300 hover:border-accent/40 ${className}`}
    >
      <header className="mb-4 flex items-center justify-between gap-3">
        <h3 className="font-display text-lg font-medium tracking-tight text-fg">{title}</h3>
        {action}
      </header>
      <div className="min-h-0 flex-1 overflow-auto">{children}</div>
    </section>
  );
}
