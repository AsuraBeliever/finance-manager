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
