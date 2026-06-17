import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  actions?: ReactNode;
}

export function PageHeader({ title, actions }: PageHeaderProps) {
  return (
    <header className="mb-7 flex flex-wrap items-end justify-between gap-3">
      <div className="flex items-center gap-3">
        <span className="h-7 w-1 shrink-0 rounded-full bg-gold" aria-hidden />
        <h2 className="font-display text-[1.9rem] font-medium leading-none tracking-tight text-fg">
          {title}
        </h2>
      </div>
      {actions}
    </header>
  );
}
