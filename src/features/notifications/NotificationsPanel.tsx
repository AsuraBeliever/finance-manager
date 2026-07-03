import { useEffect, useMemo, useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { formatDistanceToNow } from "date-fns";
import { es as dateLocaleEs, enUS as dateLocaleEn } from "date-fns/locale";
import { CreditCard, PiggyBank, Repeat, TrendingUp, BellOff } from "lucide-react";
import { Link } from "react-router-dom";
import { Modal } from "../../components/Modal";
import { markNotificationsRead } from "../../lib/api";
import type { NotificationList } from "../../lib/types";
import { es } from "../../i18n/es";
import { getLocale } from "../../i18n/store";
import { notificationCategory, renderNotification } from "./render";

/** SQLite UTC timestamp ("YYYY-MM-DD HH:MM:SS") → relative phrase. */
function relTime(utc: string): string {
  const date = new Date(`${utc.replace(" ", "T")}Z`);
  if (Number.isNaN(date.getTime())) return "";
  const locale = getLocale() === "en" ? dateLocaleEn : dateLocaleEs;
  return formatDistanceToNow(date, { addSuffix: true, locale });
}

const CATEGORY_ICONS: Record<string, typeof CreditCard> = {
  credit: CreditCard,
  goal: PiggyBank,
  sub: Repeat,
  inv: TrendingUp,
};

interface Props {
  open: boolean;
  onClose: () => void;
  list: NotificationList | undefined;
}

export function NotificationsPanel({ open, onClose, list }: Props) {
  const queryClient = useQueryClient();
  const markRead = useMutation({
    mutationFn: () => markNotificationsRead(),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  // Opening the panel clears the badge, but the rows keep their "new"
  // highlight from the snapshot taken at open until the panel closes.
  const unreadAtOpen = useRef<Set<number>>(new Set());
  const items = list?.items ?? [];
  useEffect(() => {
    if (!open) return;
    unreadAtOpen.current = new Set(
      items.filter((n) => n.readAt === null).map((n) => n.id),
    );
    if ((list?.unreadCount ?? 0) > 0) markRead.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const rows = useMemo(
    () =>
      items.map((n) => ({
        ...n,
        text: renderNotification(n.kind, n.paramsJson),
        isNew: n.readAt === null || unreadAtOpen.current.has(n.id),
        Icon: CATEGORY_ICONS[notificationCategory(n.kind)] ?? CreditCard,
      })),
    [items, open], // eslint-disable-line react-hooks/exhaustive-deps
  );

  return (
    <Modal title={es.notifications.title} open={open} onClose={onClose}>
      {rows.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-8 text-center">
          <BellOff size={28} className="text-fg-subtle" />
          <p className="text-sm text-fg-muted">{es.notifications.empty}</p>
          <Link
            to="/ajustes/notificaciones"
            onClick={onClose}
            className="text-sm font-medium text-accent hover:underline"
          >
            {es.notifications.manage}
          </Link>
        </div>
      ) : (
        <ul className="-mx-2 flex flex-col">
          {rows.map((n) => (
            <li
              key={n.id}
              className={`flex items-start gap-3 rounded-xl px-3 py-3 ${
                n.isNew ? "bg-accent-dim/10" : ""
              }`}
            >
              <span
                className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                  n.isNew ? "bg-accent-dim/20 text-accent" : "bg-surface-overlay text-fg-subtle"
                }`}
              >
                <n.Icon size={15} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm leading-snug text-fg">{n.text}</p>
                <p className="mt-0.5 text-xs text-fg-subtle">{relTime(n.createdAt)}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {rows.length > 0 && (
        <div className="mt-3 flex items-center justify-between border-t border-border-muted pt-3">
          <Link
            to="/ajustes/notificaciones"
            onClick={onClose}
            className="text-xs text-fg-subtle transition-colors hover:text-fg"
          >
            {es.notifications.manage}
          </Link>
          <button
            onClick={() => markRead.mutate()}
            className="text-xs font-medium text-accent hover:underline"
          >
            {es.notifications.markAllRead}
          </button>
        </div>
      )}
    </Modal>
  );
}
