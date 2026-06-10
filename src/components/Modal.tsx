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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="w-full max-w-md rounded-xl border border-border-muted bg-surface-raised shadow-2xl">
        <header className="flex items-center justify-between border-b border-border-muted px-5 py-4">
          <h3 className="font-semibold">{title}</h3>
          <button
            onClick={onClose}
            aria-label={es.common.close}
            className="rounded-md p-1 text-zinc-400 transition-colors hover:bg-surface-overlay hover:text-zinc-200"
          >
            <X size={18} />
          </button>
        </header>
        <div className="px-5 py-4">{children}</div>
      </div>
    </div>
  );
}
