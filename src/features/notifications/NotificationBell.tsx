import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import { listNotifications } from "../../lib/api";
import { es } from "../../i18n/es";
import { NotificationsPanel } from "./NotificationsPanel";

/** Bell + unread badge. `floating` renders the fixed mobile button; the
 *  default inline style sits in the desktop sidebar header. */
export function NotificationBell({ floating = false }: { floating?: boolean }) {
  const [open, setOpen] = useState(false);
  const { data } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => listNotifications(30),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
  const unread = data?.unreadCount ?? 0;

  const button = (
    <button
      onClick={() => setOpen(true)}
      aria-label={es.notifications.title}
      className={
        floating
          ? "relative flex h-10 w-10 items-center justify-center rounded-full border border-border-muted bg-surface-raised/90 text-fg-muted shadow-card backdrop-blur transition-colors hover:text-fg"
          : "relative flex h-8 w-8 items-center justify-center rounded-lg text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
      }
    >
      <Bell size={floating ? 18 : 16} />
      {unread > 0 && (
        <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
          {unread > 9 ? "9+" : unread}
        </span>
      )}
    </button>
  );

  return (
    <>
      {floating ? (
        <div
          className="fixed right-3 z-30 md:hidden"
          style={{ top: "calc(env(safe-area-inset-top) + 10px)" }}
        >
          {button}
        </div>
      ) : (
        <span className="relative ml-auto">{button}</span>
      )}
      <NotificationsPanel open={open} onClose={() => setOpen(false)} list={data} />
    </>
  );
}
