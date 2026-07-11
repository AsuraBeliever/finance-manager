import { X } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect } from "react";
import { createPortal } from "react-dom";
import { es } from "../i18n/es";

interface ModalProps {
  title: string;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  /** Opaque background instead of the default frosted-glass surface. */
  solid?: boolean;
  /** Lock the card to a fixed height so it doesn't resize with its content;
   *  the body scrolls and the header stays pinned. */
  fixedHeight?: boolean;
}

export function Modal({
  title,
  open,
  onClose,
  children,
  solid = false,
  fixedHeight = false,
}: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  // Frosted glass is the default look; `solid` swaps to the opaque overlay
  // token (also sidesteps the global backdrop-filter on bg-surface-raised).
  const surface = solid ? "bg-surface-overlay" : "bg-surface-raised";
  // Fixed cards lock to 80dvh; fluid cards grow with content up to the cap.
  const height = fixedHeight ? "h-[80dvh] max-h-[90dvh]" : "max-h-[90dvh]";

  // Portal to <body>: dashboard widgets live inside react-grid-layout items
  // that carry a CSS transform, which would otherwise make this fixed overlay
  // resolve against the widget instead of the viewport (off-centre, clipped).
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm sm:p-6"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        className={`flex w-full max-w-md flex-col rounded-2xl border border-border-muted shadow-card ${surface} ${height}`}
      >
        <header className="flex shrink-0 items-center justify-between border-b border-border-muted px-5 py-4">
          <h3 className="font-display text-lg font-medium tracking-tight text-fg">{title}</h3>
          <button
            onClick={onClose}
            aria-label={es.common.close}
            className="rounded-md p-1 text-fg-muted transition-colors hover:bg-surface-overlay hover:text-fg"
          >
            <X size={18} />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
