import { useEffect } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CloudOff,
  LayoutDashboard,
  LogOut,
  Wallet,
  ArrowLeftRight,
  TrendingUp,
  Settings,
} from "lucide-react";
import { es } from "./i18n/es";
import { UNAUTHORIZED_EVENT } from "./lib/api";
import { logout, me } from "./lib/auth";
import { useOnline } from "./lib/online";
import { flush } from "./lib/outbox";
import { LoginPage } from "./features/auth/LoginPage";
import { UpdateBanner } from "./features/update/UpdateBanner";

const navItems = [
  { to: "/", label: es.nav.dashboard, icon: LayoutDashboard, end: true },
  { to: "/carteras", label: es.nav.wallets, icon: Wallet, end: false },
  {
    to: "/transacciones",
    label: es.nav.transactions,
    icon: ArrowLeftRight,
    end: false,
  },
  { to: "/inversiones", label: es.nav.investments, icon: TrendingUp, end: false },
  { to: "/ajustes", label: es.nav.settings, icon: Settings, end: false },
];

export default function App() {
  const queryClient = useQueryClient();
  const online = useOnline();
  // Always revalidate the session against the server on load: after a Google
  // OAuth redirect there is no JS continuity to setQueryData, so the app must
  // re-check /me. The persisted cache still provides instant offline render
  // (cached data shows immediately; the refetch only updates it, and on a
  // network error the cached value is kept).
  const { data: user, isLoading } = useQuery({
    queryKey: ["me"],
    queryFn: me,
    refetchOnMount: "always",
    retry: false,
  });

  // Any 401 from the API (expired session) drops back to the login screen.
  useEffect(() => {
    const onUnauthorized = () => queryClient.setQueryData(["me"], null);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [queryClient]);

  // Drain the offline outbox on startup and whenever the connection returns.
  useEffect(() => {
    const drain = () => {
      flush().then((synced) => {
        if (synced > 0) queryClient.invalidateQueries();
      });
    };
    drain();
    window.addEventListener("online", drain);
    return () => window.removeEventListener("online", drain);
  }, [queryClient]);

  const doLogout = async () => {
    await logout().catch(() => {});
    queryClient.setQueryData(["me"], null);
    queryClient.clear();
  };

  if (isLoading) {
    return (
      <div className="flex h-full flex-col">
        <UpdateBanner />
        <div className="flex flex-1 items-center justify-center text-sm text-zinc-500">
          {es.auth.checkingSession}
        </div>
      </div>
    );
  }
  if (!user) {
    return (
      <div className="flex h-full flex-col">
        <UpdateBanner />
        <div className="min-h-0 flex-1 overflow-y-auto">
          <LoginPage />
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <UpdateBanner />
      {!online && (
        <div className="flex shrink-0 items-center justify-center gap-2 bg-amber-500/15 px-4 py-1.5 text-xs text-amber-300">
          <CloudOff size={14} />
          {es.offline.banner}
        </div>
      )}
      <div className="flex min-h-0 flex-1">
        {/* Desktop sidebar */}
      <aside className="hidden w-56 shrink-0 flex-col border-r border-border-muted bg-surface-raised md:flex">
        <div className="flex items-center gap-2 px-5 py-5">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-dim/20 text-accent">
            <TrendingUp size={18} />
          </span>
          <h1 className="text-lg font-semibold tracking-tight">{es.app.name}</h1>
        </div>
        <nav className="flex flex-col gap-1 px-3">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? "bg-accent-dim/15 font-medium text-accent"
                    : "text-zinc-400 hover:bg-surface-overlay hover:text-zinc-200"
                }`
              }
            >
              <Icon size={17} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto px-3 pb-4">
          <button
            onClick={doLogout}
            title={es.auth.logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-zinc-500 transition-colors hover:bg-surface-overlay hover:text-zinc-200"
          >
            <LogOut size={17} />
            <span className="truncate">{user.email}</span>
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto px-4 py-4 pb-24 md:px-8 md:py-6 md:pb-6">
        <Outlet />
      </main>

      {/* Mobile bottom navigation */}
      <nav
        className="fixed inset-x-0 bottom-0 z-40 flex items-stretch justify-around border-t border-border-muted bg-surface-raised/95 backdrop-blur md:hidden"
        style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
      >
        {navItems.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] transition-colors ${
                isActive ? "text-accent" : "text-zinc-500"
              }`
            }
          >
            <Icon size={20} />
            {label}
          </NavLink>
        ))}
      </nav>
      </div>
    </div>
  );
}
