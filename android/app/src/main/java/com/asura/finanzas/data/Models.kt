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
data class CurrencyTotal(
    val currencyCode: String,
    val cents: Long = 0,
)

/**
 * Totals for the filtered slice of history. `byCurrency` holds the real figures
 * per wallet currency; `totalMxnCents` normalises them, and is only worth
 * showing when more than one currency is in play.
 */
@Serializable
data class TxTotals(
    val count: Long = 0,
    val totalMxnCents: Long = 0,
    val byCurrency: List<CurrencyTotal> = emptyList(),
)

@Serializable
data class MsiPlan(
    val id: Long,
    val description: String = "",
    val totalCents: Long = 0,
    val months: Int = 0,
    /** The regular installment (the first also carries the cent remainder). */
    val monthlyCents: Long = 0,
    /** Installments already part of the debt. */
    val billedMonths: Int = 0,
    val pendingCents: Long = 0,
    val nextChargeDate: String? = null,
    val nextChargeCents: Long? = null,
    val purchasedAt: String = "",
    val categoryId: Long? = null,
)

/**
 * What an MSI plan will bill and when. Computed by the worker for the live form
 * preview and echoed back on save, so the phone never works out an instalment.
 */
@Serializable
data class MsiSchedulePreview(
    val monthlyCents: Long = 0,
    val firstChargeCents: Long = 0,
    /** Cut that bills the first instalment — the statement that pays it. */
    val firstCutDate: String = "",
    val lastChargeDate: String = "",
    val months: Int = 0,
    /** Instalments that join the debt at once, when the purchase is back-dated. */
    val alreadyBilledMonths: Int = 0,
    val alreadyBilledCents: Long = 0,
)

@Serializable
data class CreditStatement(
    val cutDate: String = "",
    /** Pay this in full by dueDate and no interest accrues. */
    val balanceCents: Long = 0,
    val paidCents: Long = 0,
    val remainingCents: Long = 0,
    val dueDate: String = "",
    /** Negative = past due. */
    val daysToDue: Int = 0,
)

@Serializable
data class CreditCardSummary(
    val debtCents: Long = 0,
    val creditLimitCents: Long? = null,
    /** limit − debt − unbilled MSI; null when the limit is untracked. */
    val availableCreditCents: Long? = null,
    /** (debt + unbilled MSI) ÷ limit in basis points; null without a limit. */
    val utilizationBps: Long? = null,
    val nextCutDate: String = "",
    val daysToCut: Int = 0,
    val statement: CreditStatement = CreditStatement(),
    val nextAnniversary: String? = null,
    /** MSI amounts committed but not billed yet. */
    val pendingMsiCents: Long = 0,
    val msiPlans: List<MsiPlan> = emptyList(),
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
    /** Present only when both a deadline and a cadence are set. */
    val plan: ContributionPlan? = null,
    /** True when the goal has fallen below its steady pace. */
    val isBehind: Boolean = false,
    /** "purchase" (completing spends it) or "fund" (drawn down over time). */
    val goalKind: String = "purchase",
)

/**
 * What to set aside each period to arrive on time. Computed in Rust — the app
 * only picks which sentence to show.
 */
@Serializable
data class ContributionPlan(
    /** Cadence periods left until the deadline (0 when met or overdue). */
    val periodsLeft: Long = 0,
    val perPeriodCents: Long = 0,
    /**
     * This period's quota, frozen at the period start: it does not shrink as
     * you contribute, so partial progress reads "2,000 of 2,400".
     */
    val periodQuotaCents: Long = 0,
    /** Still missing to cover this period's quota (0 = covered). */
    val periodMissingCents: Long = 0,
    val contributedThisPeriodCents: Long = 0,
    /** Whole days to the deadline; negative once it has passed. */
    val daysLeft: Long = 0,
    val overdue: Boolean = false,
    /** How far below the steady pace the saved amount is (0 = on/ahead). */
    val behindCents: Long = 0,
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
    /** Calculator inputs (rate, term, …) as stored JSON; edited, never computed. */
    val paramsJson: String = "{}",
    val linkedWalletId: Long? = null,
    val isClosed: Boolean = false,
    val notes: String? = null,
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

/**
 * One movement with everything the editor needs (`get_investment_movement`).
 * The list shape does not carry `walletId`, so editing reads it from here.
 */
@Serializable
data class MovementDetail(
    val id: Long,
    val investmentId: Long,
    val investmentName: String = "",
    /** "deposit" | "withdrawal". */
    val kind: String,
    val amountCents: Long = 0,
    val occurredAt: String,
    val walletId: Long? = null,
    val currencyCode: String = "MXN",
    val startDate: String = "",
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
    val paramsJson: String = "{}",
    val linkedWalletId: Long? = null,
    val isClosed: Boolean = false,
    val notes: String? = null,
    val currentValueCents: Long = 0,
    val netInvestedCents: Long = 0,
    val gainCents: Long = 0,
    val maturityDate: String? = null,
    val projection: List<ProjectionPoint> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val movements: List<InvestmentMovement> = emptyList(),
)

/**
 * A ready-made investment the web offers in its catalog (CETES 28/91/…, Nu
 * cajita, BONDDIA…). `paramsJson` is the calculator's own configuration and is
 * passed through untouched — the app never authors or edits those parameters.
 */
@Serializable
data class CatalogItem(
    val id: String,
    val calculator: String,
    val paramsJson: String = "{}",
    /** The live rate the server found, in basis points; null when unknown. */
    val rateBps: Long? = null,
    val rateDate: String? = null,
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

/**
 * Both legs of a transfer read as one thing (worker: `get_transfer`), so the
 * edit form can prefill from/to wallets and amounts. The legs always share
 * group, note, date and time.
 */
@Serializable
data class TransferDetail(
    /** The `transfer_out` leg's id. */
    val id: Long,
    val fromWalletId: Long,
    val toWalletId: Long,
    val amountFromCents: Long,
    val amountToCents: Long,
    val description: String? = null,
    val occurredAt: String,
    val occurredTime: String? = null,
)

/** A published Banxico rate: basis points and the day it was published. */
@Serializable
data class BanxicoRate(
    val rateBps: Long,
    val date: String,
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

// ---- simulator ----
//
// Pure projections: no account data involved, and every figure below is
// computed by finanzas-core on the server. The phone only supplies the inputs.

@Serializable
data class SimPoint(
    val month: Int = 0,
    val contributedCents: Long = 0,
    val valueCents: Long = 0,
)

@Serializable
data class SimResult(
    val points: List<SimPoint> = emptyList(),
    val finalValueCents: Long = 0,
    val totalContributedCents: Long = 0,
    val totalInterestCents: Long = 0,
)

@Serializable
data class SolveResult(
    /** What you would have to put in monthly to hit the target on time. */
    val monthlyContributionCents: Long = 0,
)


/** `project_investment`: the same curve the web's detail chart draws. */
@Serializable
data class InvestmentProjection(
    val projection: List<ProjectionPoint> = emptyList(),
    val annualRateBps: Long? = null,
    val finalValueCents: Long = 0,
    val contributedCents: Long = 0,
    val interestCents: Long = 0,
)
