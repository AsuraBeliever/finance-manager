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
  /** Total earmarked in active goal apartados; available = balance − reserved. */
  reservedCents: number;
  color: string | null;
  skin: string | null;
  notes: string | null;
  /** Parent wallet this is an apartado (pocket) of, or null when standalone. */
  parentWalletId: number | null;
  isArchived: boolean;
  /** Annual yield rate in basis points, or null when the wallet earns nothing. */
  yieldRateBps: number | null;
  /** Payout cadence ('weekly' | 'biweekly' | 'monthly') when yield is on. */
  yieldFrequency: string | null;
  /** 'YYYY-MM-DD' the day yield was switched on (null when off). */
  yieldAnchorDate: string | null;
  /** Day of month (1-31) the statement closes; set = this is a credit card. */
  creditCutDay: number | null;
  /** Days after the cut to pay without interest (null = default 20). */
  creditDueDays: number | null;
  /** Credit line in cents, or null when untracked. */
  creditLimitCents: number | null;
  /** 'MM-DD' the bank charges the annual fee, or null when untracked. */
  creditAnniversary: string | null;
  createdAt: string;
}

/** One purchase in meses sin intereses, with its billing progress. */
export interface MsiPlan {
  id: number;
  description: string;
  totalCents: number;
  months: number;
  /** The regular installment (the first also carries the cent remainder). */
  monthlyCents: number;
  billedMonths: number;
  pendingCents: number;
  /** Null once the plan is fully billed. */
  nextChargeDate: string | null;
  nextChargeCents: number | null;
  purchasedAt: string;
}

/** The last closed statement of a credit card and how it stands today. */
export interface CreditStatement {
  cutDate: string;
  /** Saldo al corte — pay this in full by dueDate and no interest accrues. */
  balanceCents: number;
  /** Payments posted after the cut. */
  paidCents: number;
  /** balance − paid, floored at zero. */
  remainingCents: number;
  dueDate: string;
  /** Negative = past due. */
  daysToDue: number;
}

export interface CreditCardSummary {
  debtCents: number;
  creditLimitCents: number | null;
  /** limit − debt − unbilled MSI; null when the limit isn't tracked. */
  availableCreditCents: number | null;
  /** (debt + unbilled MSI) ÷ limit in basis points; null without a limit. */
  utilizationBps: number | null;
  nextCutDate: string;
  daysToCut: number;
  statement: CreditStatement;
  nextAnniversary: string | null;
  /** MSI amounts not yet billed — committed but not in the debt yet. */
  pendingMsiCents: number;
  msiPlans: MsiPlan[];
}

export type TransactionKind = "income" | "expense" | "transfer_in" | "transfer_out";

/** A transaction row's kind as shown in the history. Beyond the real kinds, the
 *  list also surfaces read-only apartado moves to/from goals ("reserve" /
 *  "release") for tracking — these never affect balances. */
export type TxRowKind = TransactionKind | "reserve" | "release";

export interface TransactionCategory {
  id: number;
  name: string;
  kind: "income" | "expense";
  icon: string | null;
  color: string | null;
  isSystem: boolean;
  /** A seed category hidden for this user. Always false for own categories. */
  isHidden: boolean;
}

export interface Transaction {
  id: number;
  walletId: number;
  walletName: string;
  kind: TxRowKind;
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

export interface InvestmentSlice {
  id: number;
  name: string;
  valueMxnCents: number;
}

export interface DashboardSummary {
  /** Cash (wallets) total at the start of the selected period. */
  totalStartMxnCents: number;
  /** Cash (wallets) total at the end of the period. */
  totalEndMxnCents: number;
  wallets: WalletBalance[];
  byCurrency: CurrencySubtotal[];
  missingRates: string[];
  investmentsStartMxnCents: number;
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

export interface InvestmentProjection {
  projection: ProjectionPoint[];
  annualRateBps: number | null;
  finalValueCents: number;
  contributedCents: number;
  interestCents: number;
}

// ---- forward simulator ----

export type SimCadence = "monthly" | "biweekly" | "weekly" | "none";

export interface SimulateInput {
  initialCents: number;
  contributionCents: number;
  cadence: SimCadence;
  annualRateBps: number;
  months: number;
}

export interface SimPoint {
  month: number;
  contributedCents: number;
  valueCents: number;
}

export interface SimResult {
  points: SimPoint[];
  finalValueCents: number;
  totalContributedCents: number;
  totalInterestCents: number;
}

export interface SolveInput {
  initialCents: number;
  targetCents: number;
  annualRateBps: number;
  months: number;
}

export interface SolveResult {
  monthlyContributionCents: number;
}

export interface PortfolioSlice {
  id: number;
  name: string;
  currentValueCents: number;
  gainCents: number;
}

export interface Portfolio {
  totalValueCents: number;
  totalInvestedCents: number;
  totalGainCents: number;
  annualizedReturnBps: number | null;
  slices: PortfolioSlice[];
}

// ---- analytics ----

export interface CategorySlice {
  categoryId: number | null;
  name: string;
  color: string | null;
  icon: string | null;
  mxnCents: number;
}

export interface CategoryBreakdown {
  totalMxnCents: number;
  slices: CategorySlice[];
}

/** Dashboard flow window. Mirrors `finanzas_core::period::Period` (tagged by
 *  `kind`, camelCase). `month` is 1-12; dates are ISO `YYYY-MM-DD`. */
export type Period =
  | { kind: "currentMonth" }
  | { kind: "lastMonths"; months: number }
  | { kind: "month"; year: number; month: number }
  | { kind: "day"; date: string }
  | { kind: "range"; from: string; to: string };

export type BucketUnit = "day" | "month";

export interface FlowBucket {
  /** 'YYYY-MM-DD' when bucketUnit is 'day', 'YYYY-MM' when 'month'. */
  key: string;
  incomeMxnCents: number;
  expenseMxnCents: number;
}

export interface SpendingTrends {
  incomeMxnCents: number;
  expenseMxnCents: number;
  incomePrevMxnCents: number;
  expensePrevMxnCents: number;
  incomeTrendBps: number;
  expenseTrendBps: number;
  bucketUnit: BucketUnit;
  buckets: FlowBucket[];
}

// ---- savings goals ----

/** How often the user plans to contribute toward a goal with a deadline. */
export type GoalCadence = "daily" | "weekly" | "monthly" | "yearly";

/** What a goal is for: buying something (completing spends it) or a savings
 *  fund (you draw it down over time, or graduate it into its own wallet). */
export type GoalKind = "purchase" | "fund";

/** Contribution plan for a goal with a deadline (computed in Rust). */
export interface ContributionPlan {
  /** Cadence periods left until the deadline (0 when met or overdue). */
  periodsLeft: number;
  /** Suggested amount to set aside each period to arrive on time. */
  perPeriodCents: number;
  /** Whole days to the deadline; negative once it has passed. */
  daysLeft: number;
  /** True once the deadline passed with money still owed. */
  overdue: boolean;
  /** How far below the steady pace the saved amount is (0 = on/ahead). */
  behindCents: number;
}

export interface SavingsGoal {
  id: number;
  name: string;
  icon: string | null;
  color: string | null;
  currencyCode: string;
  targetCents: number;
  savedCents: number;
  progressBps: number;
  /** Wallet remembered as the default source for contributions, if any. */
  linkedWalletId: number | null;
  /** Deadline 'YYYY-MM-DD' to reach the target by, if set. */
  targetDate: string | null;
  /** Contribution cadence, set alongside the deadline. */
  cadence: GoalCadence | null;
  /** Plan, present only when both a deadline and cadence are set. */
  plan: ContributionPlan | null;
  /** True when the goal has fallen below its steady pace. */
  isBehind: boolean;
  /** Purchase (completing spends it) or fund (savings you draw down). */
  goalKind: GoalKind;
}

export interface GoalInput {
  name: string;
  icon: string | null;
  color: string | null;
  currencyCode: string;
  targetCents: number;
  /** Wallet the goal reserves from (required). */
  walletId: number | null;
  /** Optional deadline 'YYYY-MM-DD'. */
  targetDate: string | null;
  /** Contribution cadence (defaults to monthly when a deadline is set). */
  cadence: GoalCadence | null;
  /** Purchase or fund. */
  goalKind: GoalKind;
}

// ---- budgets ----

export interface Budget {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  color: string | null;
  limitCents: number;
  spentMxnCents: number;
  progressBps: number;
}

// ---- subscriptions ----

export type Cadence = "monthly" | "yearly";

export interface Subscription {
  id: number;
  name: string;
  icon: string | null;
  color: string | null;
  amountCents: number;
  currencyCode: string;
  cadence: Cadence;
  nextChargeDate: string;
  walletId: number | null;
  categoryId: number | null;
  isActive: boolean;
  /** Whether a charge falls inside the selected dashboard period. */
  chargedInPeriod: boolean;
}

export interface SubscriptionList {
  subscriptions: Subscription[];
  monthlyTotalMxnCents: number;
}

export interface SubInput {
  name: string;
  icon: string | null;
  color: string | null;
  amountCents: number;
  currencyCode: string;
  cadence: Cadence;
  nextChargeDate: string;
  walletId: number | null;
  categoryId: number | null;
}
