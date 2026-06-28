import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

interface PageHeaderProps {
  title: string;
  actions?: ReactNode;
  /** When set, renders a back link above the title pointing to this route. */
  backTo?: string;
  /** Label for the back link (required when backTo is set). */
  backLabel?: string;
}

export function PageHeader({ title, actions, backTo, backLabel }: PageHeaderProps) {
  return (
    <div className="mb-7">
      {backTo && (
        <Link
          to={backTo}
          className="mb-3 inline-flex items-center gap-1.5 text-sm text-fg-subtle transition-colors hover:text-fg"
        >
          <ArrowLeft size={15} /> {backLabel}
        </Link>
      )}
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="h-7 w-1 shrink-0 rounded-full bg-gold" aria-hidden />
          <h2 className="font-display text-[1.9rem] font-medium leading-none tracking-tight text-fg">
            {title}
          </h2>
        </div>
        {actions}
      </header>
    </div>
  );
}
