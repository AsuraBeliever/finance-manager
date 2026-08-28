package com.asura.finanzas.data

import kotlinx.serialization.Serializable

// Wire shapes for the RPC API. These mirror src/lib/types.ts — the backend
// serializes camelCase, so field names match one-to-one.
//
// Money is always integer cents and rates are basis points: the app NEVER does
// arithmetic on these beyond formatting. Every balance, yield and valuation
// arrives already computed by Rust on the server.

@Serializable
data class User(
    val id: Long,
    val email: String,
)

@Serializable
data class Wallet(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String = "",
    val currencyCode: String,
    val initialBalanceCents: Long = 0,
    val balanceCents: Long,
    /** Earmarked in active goal apartados; available = balance − reserved. */
    val reservedCents: Long = 0,
    val color: String? = null,
    val skin: String? = null,
    val notes: String? = null,
    val parentWalletId: Long? = null,
    val isArchived: Boolean = false,
    /** Annual yield in basis points, or null when the wallet earns nothing. */
    val yieldRateBps: Long? = null,
    val yieldFrequency: String? = null,
    val yieldAnchorDate: String? = null,
    /** Set = this wallet is a credit card (statement closing day). */
    val creditCutDay: Int? = null,
    val creditDueDays: Int? = null,
    val creditLimitCents: Long? = null,
    /** 'MM-DD' the bank charges the annual fee, or null when untracked. */
    val creditAnniversary: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class WalletBalance(
    val walletId: Long,
    val name: String,
    val color: String? = null,
    val currencyCode: String,
    val balanceCents: Long,
    val balanceMxnCents: Long,
)

@Serializable
data class CurrencySubtotal(
    val currencyCode: String,
    val balanceCents: Long,
    val balanceMxnCents: Long,
    /** False when no exchange rate is known — the MXN figure is then a guess. */
    val hasRate: Boolean = true,
)

@Serializable
data class InvestmentSlice(
    val id: Long,
    val name: String,
    val valueMxnCents: Long,
)

@Serializable
data class DashboardSummary(
    /** Cash (wallets) total at the start of the selected period. */
    val totalStartMxnCents: Long = 0,
    /** Cash (wallets) total at the end of the period. */
    val totalEndMxnCents: Long = 0,
    val wallets: List<WalletBalance> = emptyList(),
    val byCurrency: List<CurrencySubtotal> = emptyList(),
    val missingRates: List<String> = emptyList(),
    val investmentsStartMxnCents: Long = 0,
    val investmentsTotalMxnCents: Long = 0,
    val investments: List<InvestmentSlice> = emptyList(),
)

@Serializable
data class SessionInfo(
    val id: Long,
    val createdAt: String = "",
    val lastSeenAt: String? = null,
    val userAgent: String? = null,
    /** True for the session this device is using; the UI never offers to revoke it. */
    val current: Boolean = false,
)

@Serializable
data class WalletCategory(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val isSystem: Boolean = false,
)

@Serializable
data class Currency(
    val code: String,
    val name: String = "",
    val symbol: String = "",
    val decimals: Int = 2,
)

@Serializable
data class ExchangeRate(
    val currencyCode: String,
    /** Rate to MXN in micros — never multiplied here, only displayed. */
    val rateToMxnMicros: Long = 0,
    val asOf: String = "",
    val source: String = "",
)

@Serializable
data class SavingsGoal(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val currencyCode: String,
    val targetCents: Long = 0,
    val savedCents: Long = 0,
    /** Progress in basis points, computed server-side. */
    val progressBps: Long = 0,
    val linkedWalletId: Long? = null,
    val targetDate: String? = null,
    val cadence: String? = null,
    /** True when the goal has fallen below its steady pace. */
    val isBehind: Boolean = false,
    /** "purchase" (completing spends it) or "fund" (drawn down over time). */
    val goalKind: String = "purchase",
)

@Serializable
data class Budget(
    val id: Long,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val color: String? = null,
    val limitCents: Long = 0,
    val spentMxnCents: Long = 0,
    val progressBps: Long = 0,
)

@Serializable
data class Subscription(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val amountCents: Long = 0,
    val currencyCode: String = "MXN",
    /** "monthly" | "yearly". */
    val cadence: String = "monthly",
    val nextChargeDate: String = "",
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val isActive: Boolean = true,
    val chargedInPeriod: Boolean = false,
)

@Serializable
data class SubscriptionList(
    val subscriptions: List<Subscription> = emptyList(),
    val monthlyTotalMxnCents: Long = 0,
)

@Serializable
data class Investment(
    val id: Long,
    val name: String,
    val calculator: String,
    val currencyCode: String,
    val principalCents: Long = 0,
    val startDate: String = "",
    val linkedWalletId: Long? = null,
    val isClosed: Boolean = false,
    val currentValueCents: Long = 0,
    /** principal + contributions − withdrawals, computed server-side. */
    val netInvestedCents: Long = 0,
    /** current value − net invested, computed server-side. */
    val gainCents: Long = 0,
    val maturityDate: String? = null,
)

@Serializable
data class ProjectionPoint(
    val date: String,
    val valueCents: Long = 0,
)

@Serializable
data class InvestmentSnapshot(
    val id: Long,
    val investmentId: Long,
    val valueCents: Long = 0,
    val asOf: String,
    val source: String = "",
)

@Serializable
data class InvestmentMovement(
    val id: Long,
    val investmentId: Long,
    /** "deposit" | "withdrawal". */
    val kind: String,
    val amountCents: Long = 0,
    val occurredAt: String,
)

/** `get_investment_detail`: the investment plus its history and projection. */
@Serializable
data class InvestmentDetail(
    val id: Long,
    val name: String,
    val calculator: String,
    val currencyCode: String,
    val principalCents: Long = 0,
    val startDate: String = "",
    val linkedWalletId: Long? = null,
    val isClosed: Boolean = false,
    val currentValueCents: Long = 0,
    val netInvestedCents: Long = 0,
    val gainCents: Long = 0,
    val maturityDate: String? = null,
    val projection: List<ProjectionPoint> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val movements: List<InvestmentMovement> = emptyList(),
)

@Serializable
data class PortfolioSlice(
    val id: Long,
    val name: String,
    val currentValueCents: Long = 0,
    val gainCents: Long = 0,
)

@Serializable
data class Portfolio(
    val totalValueCents: Long = 0,
    val totalInvestedCents: Long = 0,
    val totalGainCents: Long = 0,
    /** Annualised return in basis points; null when it cannot be computed. */
    val annualizedReturnBps: Long? = null,
    val slices: List<PortfolioSlice> = emptyList(),
)

@Serializable
data class FlowBucket(
    /** 'YYYY-MM-DD' when bucketUnit is 'day', 'YYYY-MM' when 'month'. */
    val key: String,
    val incomeMxnCents: Long = 0,
    val expenseMxnCents: Long = 0,
)

@Serializable
data class SpendingTrends(
    val incomeMxnCents: Long = 0,
    val expenseMxnCents: Long = 0,
    val incomePrevMxnCents: Long = 0,
    val expensePrevMxnCents: Long = 0,
    /** Change vs the previous window, in basis points (server-computed). */
    val incomeTrendBps: Long = 0,
    val expenseTrendBps: Long = 0,
    val bucketUnit: String = "day",
    val buckets: List<FlowBucket> = emptyList(),
)

@Serializable
data class CategorySlice(
    val categoryId: Long? = null,
    val name: String = "",
    val color: String? = null,
    val icon: String? = null,
    val mxnCents: Long = 0,
)

@Serializable
data class CategoryBreakdown(
    val totalMxnCents: Long = 0,
    val slices: List<CategorySlice> = emptyList(),
)

@Serializable
data class Transaction(
    val id: Long,
    val walletId: Long,
    val walletName: String = "",
    /** "income" | "expense" | "transfer_in" | "transfer_out". */
    val kind: String,
    val amountCents: Long,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val transferGroupId: String? = null,
    val description: String? = null,
    val occurredAt: String,
    /** Local wall-clock 'HH:MM' the movement happened, or null if untimed. */
    val occurredTime: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class TransactionCategory(
    val id: Long,
    val name: String,
    /** "income" | "expense". */
    val kind: String,
    val icon: String? = null,
    val color: String? = null,
    val isSystem: Boolean = false,
    val isHidden: Boolean = false,
)
