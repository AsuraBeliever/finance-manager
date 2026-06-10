import React from "react";
import ReactDOM from "react-dom/client";
import { createHashRouter, RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { WalletsPage } from "./features/wallets/WalletsPage";
import { WalletDetailPage } from "./features/wallets/WalletDetailPage";
import { TransactionsPage } from "./features/transactions/TransactionsPage";
import { InvestmentsPage } from "./features/investments/InvestmentsPage";
import { InvestmentDetailPage } from "./features/investments/InvestmentDetailPage";
import { SettingsPage } from "./features/settings/SettingsPage";
import "./index.css";

const queryClient = new QueryClient();

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
      { path: "ajustes", element: <SettingsPage /> },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </React.StrictMode>,
);
