import type {
  CalculatorId,
  Currency,
  DashboardSummary,
  ExchangeRate,
  InvestmentDetail,
  InvestmentWithValue,
  Transaction,
  TransactionCategory,
  TransactionKind,
  Wallet,
  WalletCategory,
} from "./types";

// One typed wrapper per backend command. Components never fetch directly.
//
// Transport: POST /api/rpc/<command> against the Cloudflare Worker, with the
// same camelCase argument shapes the Tauri invoke bridge used. Errors arrive
// as {"error": "..."} and are rethrown as Error(message), matching the old
// string-rejection contract.

/** Fired when any call returns 401 so the app can show the login screen. */
export const UNAUTHORIZED_EVENT = "auth:unauthorized";

export async function rpc<T>(
  name: string,
  args?: Record<string, unknown>,
): Promise<T> {
  const res = await fetch(`/api/rpc/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify(args ?? {}),
  });
  if (res.status === 401) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? `Error ${res.status}`);
  }
  return (await res.json()) as T;
}

export const listCurrencies = () => rpc<Currency[]>("list_currencies");

export const listWalletCategories = () =>
  rpc<WalletCategory[]>("list_wallet_categories");

export const getExchangeRates = () => rpc<ExchangeRate[]>("get_exchange_rates");

/** Refresh market caches (Banxico rate history + crypto prices). */
export const refreshMarketData = () => rpc<void>("refresh_market_data_cmd");

export const addCurrency = (code: string, name: string, symbol: string) =>
  rpc<Currency>("add_currency", { code, name, symbol });

export interface WalletInput {
  name: string;
  categoryId: number;
  currencyCode: string;
  initialBalanceCents: number;
  color: string | null;
  notes: string | null;
}

export const listWallets = (includeArchived = false) =>
  rpc<Wallet[]>("list_wallets", { includeArchived });

export const getWallet = (id: number) => rpc<Wallet>("get_wallet", { id });

export const createWallet = (input: WalletInput) =>
  rpc<Wallet>("create_wallet", { ...input });

export const updateWallet = (id: number, input: WalletInput) =>
  rpc<Wallet>("update_wallet", { id, ...input });

export const archiveWallet = (id: number, archived: boolean) =>
  rpc<void>("archive_wallet", { id, archived });

export const deleteWallet = (id: number) => rpc<void>("delete_wallet", { id });

export interface SimpleTxInput {
  walletId: number;
  amountCents: number;
  categoryId: number | null;
  description: string | null;
  occurredAt: string;
}

export interface TransferInput {
  fromWalletId: number;
  toWalletId: number;
  amountFromCents: number;
  amountToCents: number;
  description: string | null;
  occurredAt: string;
}

export interface TxFilter {
  walletId?: number;
  kind?: TransactionKind;
  categoryId?: number;
  from?: string;
  to?: string;
  limit?: number;
  offset?: number;
}

export const addIncome = (input: SimpleTxInput) =>
  rpc<number>("add_income", { ...input });

export const addExpense = (input: SimpleTxInput) =>
  rpc<number>("add_expense", { ...input });

export const addTransfer = (input: TransferInput) =>
  rpc<string>("add_transfer", { ...input });

export const listTransactions = (filter: TxFilter = {}) =>
  rpc<Transaction[]>("list_transactions", { filter });

export const deleteTransaction = (id: number) =>
  rpc<void>("delete_transaction", { id });

export const listTransactionCategories = () =>
  rpc<TransactionCategory[]>("list_transaction_categories");

export const createTransactionCategory = (name: string, kind: "income" | "expense") =>
  rpc<number>("create_transaction_category", { name, kind });

export const getDashboardSummary = () =>
  rpc<DashboardSummary>("get_dashboard_summary");

export interface InvestmentInput {
  name: string;
  currencyCode: string;
  principalCents: number;
  startDate: string;
  paramsJson: string;
  linkedWalletId: number | null;
  notes: string | null;
}

export const listCalculators = () => rpc<CalculatorId[]>("list_calculators");

export interface CatalogItem {
  id: string;
  calculator: CalculatorId;
  paramsJson: string;
  rateBps: number | null;
  rateDate: string | null;
}

export const getInvestmentCatalog = () =>
  rpc<CatalogItem[]>("get_investment_catalog");

export const listInvestments = (includeClosed = false) =>
  rpc<InvestmentWithValue[]>("list_investments", { includeClosed });

export const createInvestment = (calculator: CalculatorId, input: InvestmentInput) =>
  rpc<InvestmentWithValue>("create_investment", { calculator, ...input });

export const updateInvestment = (id: number, input: InvestmentInput) =>
  rpc<InvestmentWithValue>("update_investment", { id, ...input });

export const closeInvestment = (id: number, closed: boolean) =>
  rpc<void>("close_investment", { id, closed });

export const deleteInvestment = (id: number) =>
  rpc<void>("delete_investment", { id });

export const addSnapshot = (investmentId: number, valueCents: number, asOf: string) =>
  rpc<void>("add_snapshot", { investmentId, valueCents, asOf });

export const getInvestmentDetail = (id: number) =>
  rpc<InvestmentDetail>("get_investment_detail", { id });

export const addInvestmentMovement = (
  investmentId: number,
  kind: "deposit" | "withdrawal",
  amountCents: number,
  occurredAt: string,
) =>
  rpc<void>("add_investment_movement", {
    investmentId,
    kind,
    amountCents,
    occurredAt,
  });

export const deleteInvestmentMovement = (id: number) =>
  rpc<void>("delete_investment_movement", { id });

export type BanxicoSeriesKind =
  | "cetes_28"
  | "cetes_91"
  | "cetes_182"
  | "cetes_364"
  | "objetivo";

export interface BanxicoRate {
  rateBps: number;
  date: string;
}

export const fetchBanxicoRate = (kind: BanxicoSeriesKind) =>
  rpc<BanxicoRate>("fetch_banxico_rate", { kind });
