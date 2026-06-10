import { invoke } from "@tauri-apps/api/core";
import type { Currency, ExchangeRate, WalletCategory } from "./types";

// One typed wrapper per Tauri command. Components never call invoke() directly.

export const listCurrencies = () => invoke<Currency[]>("list_currencies");

export const listWalletCategories = () =>
  invoke<WalletCategory[]>("list_wallet_categories");

export const getExchangeRates = () => invoke<ExchangeRate[]>("get_exchange_rates");

export const setExchangeRate = (currencyCode: string, rateToMxnMicros: number) =>
  invoke<void>("set_exchange_rate", { currencyCode, rateToMxnMicros });

export const addCurrency = (code: string, name: string, symbol: string) =>
  invoke<Currency>("add_currency", { code, name, symbol });
