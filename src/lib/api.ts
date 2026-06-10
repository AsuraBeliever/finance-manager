import { invoke } from "@tauri-apps/api/core";
import type {
  Currency,
  DashboardSummary,
  ExchangeRate,
  Transaction,
  TransactionCategory,
  TransactionKind,
  Wallet,
  WalletCategory,
} from "./types";

// One typed wrapper per Tauri command. Components never call invoke() directly.

export const listCurrencies = () => invoke<Currency[]>("list_currencies");

export const listWalletCategories = () =>
  invoke<WalletCategory[]>("list_wallet_categories");

export const getExchangeRates = () => invoke<ExchangeRate[]>("get_exchange_rates");

export const setExchangeRate = (currencyCode: string, rateToMxnMicros: number) =>
  invoke<void>("set_exchange_rate", { currencyCode, rateToMxnMicros });

export const addCurrency = (code: string, name: string, symbol: string) =>
  invoke<Currency>("add_currency", { code, name, symbol });

export interface WalletInput {
  name: string;
  categoryId: number;
  currencyCode: string;
  initialBalanceCents: number;
  color: string | null;
  notes: string | null;
}

export const listWallets = (includeArchived = false) =>
  invoke<Wallet[]>("list_wallets", { includeArchived });

export const getWallet = (id: number) => invoke<Wallet>("get_wallet", { id });

export const createWallet = (input: WalletInput) =>
  invoke<Wallet>("create_wallet", { ...input });

export const updateWallet = (id: number, input: WalletInput) =>
  invoke<Wallet>("update_wallet", { id, ...input });

export const archiveWallet = (id: number, archived: boolean) =>
  invoke<void>("archive_wallet", { id, archived });

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
  invoke<number>("add_income", { ...input });

export const addExpense = (input: SimpleTxInput) =>
  invoke<number>("add_expense", { ...input });

export const addTransfer = (input: TransferInput) =>
  invoke<string>("add_transfer", { ...input });

export const listTransactions = (filter: TxFilter = {}) =>
  invoke<Transaction[]>("list_transactions", { filter });

export const deleteTransaction = (id: number) =>
  invoke<void>("delete_transaction", { id });

export const listTransactionCategories = () =>
  invoke<TransactionCategory[]>("list_transaction_categories");

export const createTransactionCategory = (name: string, kind: "income" | "expense") =>
  invoke<number>("create_transaction_category", { name, kind });

export const getDashboardSummary = () =>
  invoke<DashboardSummary>("get_dashboard_summary");
