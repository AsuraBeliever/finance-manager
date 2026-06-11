// TS mirrors of the Rust models in src-tauri/src/models.rs (serde camelCase).

export interface Currency {
  code: string;
  name: string;
  symbol: string;
  decimals: number;
}

export interface WalletCategory {
  id: number;
  name: string;
  icon: string | null;
  isSystem: boolean;
}

export interface Wallet {
  id: number;
  name: string;
  categoryId: number;
  categoryName: string;
  currencyCode: string;
  initialBalanceCents: number;
  balanceCents: number;
  color: string | null;
  notes: string | null;
  isArchived: boolean;
  createdAt: string;
}

export type TransactionKind = "income" | "expense" | "transfer_in" | "transfer_out";

export interface TransactionCategory {
  id: number;
  name: string;
  kind: "income" | "expense";
  icon: string | null;
  color: string | null;
  isSystem: boolean;
}

export interface Transaction {
  id: number;
  walletId: number;
  walletName: string;
  kind: TransactionKind;
  amountCents: number;
  categoryId: number | null;
  categoryName: string | null;
  transferGroupId: string | null;
  description: string | null;
  occurredAt: string;
  createdAt: string;
}

export interface ExchangeRate {
  currencyCode: string;
  rateToMxnMicros: number;
  asOf: string;
  source: string;
}

export interface WalletBalance {
  walletId: number;
  name: string;
  color: string | null;
  currencyCode: string;
  balanceCents: number;
  balanceMxnCents: number;
}

export interface CurrencySubtotal {
  currencyCode: string;
  balanceCents: number;
  balanceMxnCents: number;
  hasRate: boolean;
}

export interface MonthlyFlow {
  month: string;
  incomeMxnCents: number;
  expenseMxnCents: number;
}

export interface InvestmentSlice {
  id: number;
  name: string;
  valueMxnCents: number;
}

export interface DashboardSummary {
  totalMxnCents: number;
  wallets: WalletBalance[];
  byCurrency: CurrencySubtotal[];
  monthly: MonthlyFlow[];
  missingRates: string[];
  investmentsTotalMxnCents: number;
  investments: InvestmentSlice[];
}

export type CalculatorId =
  | "nu_cajita"
  | "cetes"
  | "bonddia"
  | "crypto"
  | "fixed_rate"
  | "manual";

export interface Investment {
  id: number;
  name: string;
  calculator: CalculatorId;
  currencyCode: string;
  principalCents: number;
  startDate: string;
  paramsJson: string;
  linkedWalletId: number | null;
  isClosed: boolean;
  notes: string | null;
  createdAt: string;
}

export interface InvestmentSnapshot {
  id: number;
  investmentId: number;
  valueCents: number;
  asOf: string;
  source: string;
}

export interface InvestmentMovement {
  id: number;
  investmentId: number;
  kind: "deposit" | "withdrawal";
  amountCents: number;
  occurredAt: string;
}

/** Investment fields are flattened together with the computed values (serde flatten). */
export type InvestmentWithValue = Investment & {
  currentValueCents: number;
  /** principal + aportaciones − retiros */
  netInvestedCents: number;
  /** valor actual − aportado neto (rendimiento realizado + no realizado) */
  gainCents: number;
  maturityDate: string | null;
};

export interface ProjectionPoint {
  date: string;
  valueCents: number;
}

export type InvestmentDetail = InvestmentWithValue & {
  projection: ProjectionPoint[];
  snapshots: InvestmentSnapshot[];
  movements: InvestmentMovement[];
};
