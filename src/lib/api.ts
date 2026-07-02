import { es } from "../i18n/es";
import type {
  Budget,
  CalculatorId,
  CategoryBreakdown,
  CreditCardSummary,
  Currency,
  MsiSchedulePreview,
  DashboardSummary,
  ExchangeRate,
  GoalInput,
  InvestmentDetail,
  InvestmentProjection,
  InvestmentWithValue,
  Portfolio,
  SimCadence,
  SimulateInput,
  SimResult,
  SolveInput,
  SolveResult,
  Period,
  SavingsGoal,
  SpendingTrends,
  SubInput,
  Subscription,
  SubscriptionList,
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

/** The request never reached the server (offline / DNS / aborted). The
 *  offline outbox retries these; real API errors are never retried. */
export class NetworkError extends Error {}

export async function rpc<T>(
  name: string,
  args?: Record<string, unknown>,
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api/rpc/${name}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "same-origin",
      body: JSON.stringify(args ?? {}),
    });
  } catch {
    throw new NetworkError(es.common.offline);
  }
  if (res.status === 401) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? `Error ${res.status}`);
  }
  return (await res.json()) as T;
}

/** Per-user key/value settings (theme, preferences). */
export const getSetting = (key: string) =>
  rpc<string | null>("get_setting", { key });

export const setSetting = (key: string, value: string) =>
  rpc<void>("set_setting", { key, value });

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
  skin: string | null;
  notes: string | null;
  /** Annual yield rate in basis points; null/omitted turns yield off. */
  yieldRateBps: number | null;
  /** Payout cadence ('weekly' | 'biweekly' | 'monthly'); used when yield is on. */
  yieldFrequency: string | null;
  /** Parent wallet to nest under as an apartado, or null for standalone. */
  parentWalletId: number | null;
  /** Day of month (1-31) the statement closes; null = not a credit card. */
  creditCutDay: number | null;
  /** Days after the cut to pay without interest (null = default 20). */
  creditDueDays: number | null;
  /** Credit line in cents, or null when untracked. */
  creditLimitCents: number | null;
  /** 'MM-DD' the bank charges the annual fee, or null when untracked. */
  creditAnniversary: string | null;
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

/** Persist a new wallet display order. `ids` are wallet ids front-to-back. */
export const reorderWallets = (ids: number[]) =>
  rpc<void>("reorder_wallets", { ids });

export const deleteWallet = (id: number) => rpc<void>("delete_wallet", { id });

export const getCreditCardSummary = (walletId: number) =>
  rpc<CreditCardSummary>("get_credit_card_summary", { walletId });

export interface MsiPlanInput {
  walletId: number;
  description: string;
  totalCents: number;
  months: number;
  /** 'YYYY-MM-DD'; omitted = today. Back-dated plans post due charges at once. */
  purchasedAt: string | null;
  /** Spending category for the installments; null = reserved MSI category. */
  categoryId: number | null;
}

/** Live schedule for the form ("≈ $X/mo · first charge on…"); no writes. */
export const previewMsiPlan = (input: Omit<MsiPlanInput, "description" | "categoryId">) =>
  rpc<MsiSchedulePreview>("preview_msi_plan", { ...input, description: "" });

/** Returns the same schedule the preview shows, for the save confirmation. */
export const createMsiPlan = (input: MsiPlanInput) =>
  rpc<MsiSchedulePreview>("create_msi_plan", { ...input });

export const deleteMsiPlan = (id: number) => rpc<void>("delete_msi_plan", { id });

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
  /** A real kind, or "transfer" to match both transfer directions. */
  kind?: TransactionKind | "transfer";
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

/** Edit an income/expense transaction. Transfers aren't editable (delete + recreate). */
export const updateTransaction = (id: number, input: SimpleTxInput) =>
  rpc<void>("update_transaction", { id, ...input });

export const deleteTransaction = (id: number) =>
  rpc<void>("delete_transaction", { id });

export const listTransactionCategories = () =>
  rpc<TransactionCategory[]>("list_transaction_categories");

/** Categories for the transactions filter — includes the reserved "Metas" so
 *  you can filter by it (the entry form uses the plain list, which hides it). */
export const listFilterCategories = () =>
  rpc<TransactionCategory[]>("list_transaction_categories", { includeReserved: true });

/** Full set for the category manager: own + all seeds, each with `isHidden`. */
export const listManageCategories = () =>
  rpc<TransactionCategory[]>("list_manage_categories");

export const createTransactionCategory = (
  name: string,
  kind: "income" | "expense",
  color?: string | null,
) => rpc<number>("create_transaction_category", { name, kind, color: color ?? null });

export const updateTransactionCategory = (
  id: number,
  name: string,
  color?: string | null,
) => rpc<void>("update_transaction_category", { id, name, color: color ?? null });

/** Own category → deleted (its transactions become uncategorized); seed
 *  category → hidden just for this user. */
export const deleteTransactionCategory = (id: number) =>
  rpc<void>("delete_transaction_category", { id });

export const restoreTransactionCategory = (id: number) =>
  rpc<void>("restore_transaction_category", { id });

/** Persist this user's category order (ids within a kind, front to back). */
export const reorderTransactionCategories = (ids: number[]) =>
  rpc<void>("reorder_transaction_categories", { ids });

export const getDashboardSummary = (period: Period = { kind: "currentMonth" }) =>
  rpc<DashboardSummary>("get_dashboard_summary", { period });

export const getCategoryBreakdown = (
  kind: "income" | "expense",
  period: Period = { kind: "currentMonth" },
) => rpc<CategoryBreakdown>("get_category_breakdown", { kind, period });

export const getSpendingTrends = (period: Period = { kind: "currentMonth" }) =>
  rpc<SpendingTrends>("get_spending_trends", { period });

// ---- savings goals ----
export const listSavingsGoals = (period: Period = { kind: "currentMonth" }) =>
  rpc<SavingsGoal[]>("list_savings_goals", { period });

export const createSavingsGoal = (input: GoalInput) =>
  rpc<SavingsGoal>("create_savings_goal", { ...input });

export const updateSavingsGoal = (id: number, input: GoalInput) =>
  rpc<SavingsGoal>("update_savings_goal", { id, ...input });

export const contributeSavingsGoal = (id: number, amountCents: number) =>
  rpc<SavingsGoal>("contribute_savings_goal", { id, amountCents });

export const useSavingsGoal = (id: number) => rpc<void>("use_savings_goal", { id });

/** Graduate a fund goal into its own wallet (moves the reserved money there).
 *  Optional style overrides the defaults (goal name/color, source category). */
export const convertGoalToWallet = (
  id: number,
  style?: {
    name?: string;
    color?: string | null;
    categoryId?: number;
    skin?: string | null;
    notes?: string | null;
    parentWalletId?: number | null;
  },
) => rpc<void>("convert_goal_to_wallet", { id, ...style });

export const deleteSavingsGoal = (id: number) =>
  rpc<void>("delete_savings_goal", { id });

export const reorderSavingsGoals = (ids: number[]) =>
  rpc<void>("reorder_savings_goals", { ids });

// ---- budgets ----
export const listBudgets = (period: Period = { kind: "currentMonth" }) =>
  rpc<Budget[]>("list_budgets", { period });

export const setBudget = (categoryId: number | null, limitCents: number) =>
  rpc<void>("set_budget", { categoryId, limitCents });

export const deleteBudget = (id: number) => rpc<void>("delete_budget", { id });

// ---- subscriptions ----
export const listSubscriptions = (period: Period = { kind: "currentMonth" }) =>
  rpc<SubscriptionList>("list_subscriptions", { period });

export const createSubscription = (input: SubInput) =>
  rpc<Subscription>("create_subscription", { ...input });

export const updateSubscription = (id: number, input: SubInput) =>
  rpc<Subscription>("update_subscription", { id, ...input });

export const setSubscriptionActive = (id: number, active: boolean) =>
  rpc<Subscription>("set_subscription_active", { id, active });

export const registerSubscriptionPayment = (id: number) =>
  rpc<Subscription>("register_subscription_payment", { id });

export const deleteSubscription = (id: number) =>
  rpc<void>("delete_subscription", { id });

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

export const simulateInvestment = (input: SimulateInput) =>
  rpc<SimResult>("simulate_investment", { ...input });

export const solveContribution = (input: SolveInput) =>
  rpc<SolveResult>("solve_contribution", { ...input });

export const getPortfolio = () => rpc<Portfolio>("get_portfolio");

export const projectInvestment = (input: {
  id: number;
  contributionCents: number;
  cadence: SimCadence;
  months: number;
}) => rpc<InvestmentProjection>("project_investment", { ...input });

export const addInvestmentMovement = (
  investmentId: number,
  kind: "deposit" | "withdrawal",
  amountCents: number,
  occurredAt: string,
  walletId: number | null = null,
) =>
  rpc<void>("add_investment_movement", {
    investmentId,
    kind,
    amountCents,
    occurredAt,
    walletId,
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
