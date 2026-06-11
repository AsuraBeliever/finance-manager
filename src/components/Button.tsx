import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "ghost" | "danger" | "dangerSolid";

const variants: Record<Variant, string> = {
  primary: "bg-accent-dim text-surface font-medium hover:bg-accent",
  ghost: "text-zinc-300 hover:bg-surface-overlay",
  danger: "text-danger hover:bg-danger/10",
  dangerSolid: "bg-danger font-medium text-surface hover:bg-red-400",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return (
    <button
      className={`rounded-lg px-4 py-2 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}
