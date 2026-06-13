import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "ghost" | "danger" | "dangerSolid";

const variants: Record<Variant, string> = {
  primary:
    "bg-accent-dim font-medium text-white shadow-[0_1px_0_rgba(255,255,255,0.12)_inset,0_8px_18px_-10px_rgba(22,164,122,0.8)] hover:bg-accent active:translate-y-px",
  ghost: "text-stone-300 hover:bg-surface-overlay",
  danger: "text-danger hover:bg-danger/10",
  dangerSolid: "bg-danger font-medium text-white hover:brightness-110 active:translate-y-px",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return (
    <button
      className={`rounded-lg px-4 py-2 text-sm transition-all duration-150 outline-none focus-visible:ring-2 focus-visible:ring-accent/50 disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
      {...props}
    />
  );
}
