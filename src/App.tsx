import { NavLink, Outlet } from "react-router-dom";
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  TrendingUp,
  Settings,
} from "lucide-react";
import { es } from "./i18n/es";

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
  return (
    <div className="flex h-full">
      <aside className="flex w-56 shrink-0 flex-col border-r border-border-muted bg-surface-raised">
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
      </aside>
      <main className="flex-1 overflow-y-auto px-8 py-6">
        <Outlet />
      </main>
    </div>
  );
}
