import React from "react";
import ReactDOM from "react-dom/client";
import { createHashRouter, RouterProvider } from "react-router-dom";
import { QueryClient } from "@tanstack/react-query";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { createSyncStoragePersister } from "@tanstack/query-sync-storage-persister";
import App from "./App";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { WalletsPage } from "./features/wallets/WalletsPage";
import { WalletDetailPage } from "./features/wallets/WalletDetailPage";
import { TransactionsPage } from "./features/transactions/TransactionsPage";
import { InvestmentsPage } from "./features/investments/InvestmentsPage";
import { InvestmentDetailPage } from "./features/investments/InvestmentDetailPage";
import { SettingsPage } from "./features/settings/SettingsPage";
import { SavingsGoalsPage } from "./features/goals/SavingsGoalsPage";
import { BudgetsPage } from "./features/budgets/BudgetsPage";
import { SubscriptionsPage } from "./features/subscriptions/SubscriptionsPage";
import { CategoriesPage } from "./features/categories/CategoriesPage";
import { useLocale } from "./i18n/store";
import "./index.css";

// Offline reads: the query cache persists to localStorage so the PWA shows
// the last-synced data without a network (with a visible offline banner —
// the service worker itself never caches /api/*). Money math still happens
// only on the server; this is a labeled snapshot, not a second source of truth.
const CACHE_MAX_AGE = 7 * 24 * 3600 * 1000; // aligned with Safari's storage eviction window

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      gcTime: CACHE_MAX_AGE, // must be >= persister maxAge or entries drop early
    },
  },
});

const persister = createSyncStoragePersister({
  storage: window.localStorage,
  key: "finanzas.cache.v1",
});

// Hash router: the production app is served from Tauri's custom protocol,
// where history-based routing has no server to rewrite paths.
const router = createHashRouter([
  {
    path: "/",
    element: <App />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "carteras", element: <WalletsPage /> },
      { path: "carteras/:id", element: <WalletDetailPage /> },
      { path: "transacciones", element: <TransactionsPage /> },
      { path: "inversiones", element: <InvestmentsPage /> },
      { path: "inversiones/:id", element: <InvestmentDetailPage /> },
      { path: "metas", element: <SavingsGoalsPage /> },
      { path: "presupuestos", element: <BudgetsPage /> },
      { path: "suscripciones", element: <SubscriptionsPage /> },
      { path: "categorias", element: <CategoriesPage /> },
      { path: "ajustes", element: <SettingsPage /> },
    ],
  },
]);

// Remount the whole router when the locale changes so every string (including
// values computed at render) re-reads the active dictionary.
function Root() {
  const locale = useLocale();
  return <RouterProvider key={locale} router={router} />;
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister,
        maxAge: CACHE_MAX_AGE,
        buster: "v2.1.0", // bump on breaking cache-shape changes
      }}
    >
      <Root />
    </PersistQueryClientProvider>
  </React.StrictMode>,
);
