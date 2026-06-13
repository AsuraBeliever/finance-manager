import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect } from "react";
import { es } from "../i18n/es";

interface ModalProps {
  title: string;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ title, open, onClose, children }: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm sm:p-6"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="max-h-[90dvh] w-full max-w-md overflow-y-auto rounded-2xl border border-border-muted bg-surface-raised shadow-card">
        <header className="flex items-center justify-between border-b border-border-muted px-5 py-4">
          <h3 className="font-display text-lg font-medium tracking-tight text-fg">{title}</h3>
          <button
            onClick={onClose}
            aria-label={es.common.close}
            className="rounded-md p-1 text-fg-muted transition-colors hover:bg-surface-overlay hover:text-fg"
          >
            <X size={18} />
          </button>
        </header>
        <div className="px-5 py-4">{children}</div>
      </div>
    </div>
  );
}
