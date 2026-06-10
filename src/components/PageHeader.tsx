import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  actions?: ReactNode;
}

export function PageHeader({ title, actions }: PageHeaderProps) {
  return (
    <header className="mb-6 flex items-center justify-between">
      <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
      {actions}
    </header>
  );
}
