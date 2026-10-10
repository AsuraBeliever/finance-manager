import { Info, TriangleAlert } from "lucide-react";
import { Button } from "./Button";
import { Modal } from "./Modal";
import { es } from "../i18n/es";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  /** "danger" (default) for deletions; "neutral" for confirmations that
   *  don't destroy anything (e.g. registering a payment). */
  tone?: "danger" | "neutral";
  onConfirm: () => void;
  onClose: () => void;
}

/** Themed replacement for window.confirm (the native dialog leaks a
 *  "JavaScript - tauri://localhost" header and ignores the app style). */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  tone = "danger",
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const danger = tone === "danger";
  return (
    <Modal title={title} open={open} onClose={onClose}>
      <div className="flex items-start gap-3">
        <span
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${
            danger ? "bg-danger/10 text-danger" : "bg-accent/10 text-accent"
          }`}
        >
          {danger ? <TriangleAlert size={17} /> : <Info size={17} />}
        </span>
        <p className="pt-1.5 text-sm text-fg">{message}</p>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} autoFocus>
          {es.common.cancel}
        </Button>
        <Button
          variant={danger ? "dangerSolid" : "primary"}
          onClick={() => {
            onConfirm();
            onClose();
          }}
        >
          {confirmLabel ?? es.common.delete}
        </Button>
      </div>
    </Modal>
  );
}
