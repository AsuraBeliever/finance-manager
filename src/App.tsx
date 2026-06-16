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
  PiggyBank,
  Target,
  CreditCard,
} from "lucide-react";
import { es } from "./i18n/es";
import { UNAUTHORIZED_EVENT } from "./lib/api";
import { logout, me } from "./lib/auth";
import { useOnline } from "./lib/online";
import { flush } from "./lib/outbox";
import { LoginPage } from "./features/auth/LoginPage";
import { UpdateBanner } from "./features/update/UpdateBanner";
import { hydrateThemeFromServer } from "./lib/theme";
import { hydrateAppearanceFromServer, useAppearance, ICONS } from "./lib/appearance";

export default function App() {
  const queryClient = useQueryClient();
  const online = useOnline();
  const appearance = useAppearance();
  const BrandIcon = ICONS[appearance.icon] ?? ICONS["trending-up"];

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
  // Desktop-only secondary group (the bottom bar stays at the 5 core items).
  const planningItems = [
    { to: "/metas", label: es.nav.goals, icon: PiggyBank },
    { to: "/presupuestos", label: es.nav.budgets, icon: Target },
    { to: "/suscripciones", label: es.nav.subscriptions, icon: CreditCard },
  ];
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

  // On login, adopt the theme saved on the account (local preference wins if
  // the call fails — offline or no server-side value yet).
  useEffect(() => {
    if (user) {
      hydrateThemeFromServer();
      hydrateAppearanceFromServer();
    }
  }, [user?.id]);

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
        <div className="flex flex-1 items-center justify-center text-sm text-fg-subtle">
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
      <aside className="hidden w-60 shrink-0 flex-col border-r border-border-muted bg-surface md:flex">
        <div className="flex items-center gap-2.5 px-5 py-6">
          {appearance.logo ? (
            <img
              src={appearance.logo}
              alt=""
              className="h-9 w-9 shrink-0 rounded-xl object-cover"
            />
          ) : (
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent-dim text-white shadow-[0_4px_12px_-4px_rgba(22,164,122,0.7)]">
              <BrandIcon size={18} />
            </span>
          )}
          <h1 className="truncate font-display text-xl font-semibold tracking-tight text-fg">
            {appearance.appName || es.app.name}
          </h1>
        </div>
        <nav className="flex flex-col gap-0.5 px-3">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ${
                  isActive
                    ? "bg-accent-dim/15 font-medium text-accent"
                    : "text-fg-muted hover:bg-surface-overlay hover:text-fg"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    className={`absolute top-1/2 left-0 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-accent transition-opacity ${
                      isActive ? "opacity-100" : "opacity-0"
                    }`}
                  />
                  <Icon size={17} />
                  {label}
                </>
              )}
            </NavLink>
          ))}

          <p className="eyebrow mt-5 mb-1 px-3">{es.nav.planning}</p>
          {planningItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ${
                  isActive
                    ? "bg-accent-dim/15 font-medium text-accent"
                    : "text-fg-muted hover:bg-surface-overlay hover:text-fg"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    className={`absolute top-1/2 left-0 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-accent transition-opacity ${
                      isActive ? "opacity-100" : "opacity-0"
                    }`}
                  />
                  <Icon size={17} />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto px-3 pb-4">
          <button
            onClick={doLogout}
            title={es.auth.logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-fg-subtle transition-colors hover:bg-surface-overlay hover:text-fg"
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
              `relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] transition-colors ${
                isActive ? "text-accent" : "text-fg-subtle"
              }`
            }
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span className="absolute top-0 h-[3px] w-8 rounded-b-full bg-accent" />
                )}
                <Icon size={20} />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      </div>
    </div>
  );
}
