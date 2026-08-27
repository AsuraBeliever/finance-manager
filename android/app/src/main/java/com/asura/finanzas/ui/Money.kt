package com.asura.finanzas.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Presentation only. Every figure the app shows was already computed in Rust on
 * the server — this converts integer cents to a string and nothing else. Never
 * add, scale or convert money here; that belongs in finanzas-core.
 */
private val esMX = Locale.forLanguageTag("es-MX")

fun formatMoney(cents: Long, currencyCode: String = "MXN", withSymbol: Boolean = true): String {
    val amount = BigDecimal.valueOf(cents, 2)
    val format = NumberFormat.getCurrencyInstance(esMX).apply {
        runCatching { currency = Currency.getInstance(currencyCode) }
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    val text = format.format(amount)
    return if (withSymbol) text else text.replace(Regex("[^0-9.,\\-]"), "").trim()
}

/** "MXN 1,234.56" style suffix used when several currencies share a screen. */
fun formatMoneyWithCode(cents: Long, currencyCode: String): String =
    "${formatMoney(cents, currencyCode)} $currencyCode"

/** Signed, for deltas: "+$120.00" / "−$120.00" (true minus, not a hyphen). */
fun formatDelta(cents: Long, currencyCode: String = "MXN"): String {
    val body = formatMoney(kotlin.math.abs(cents), currencyCode)
    return when {
        cents > 0 -> "+$body"
        cents < 0 -> "−$body"
        else -> body
    }
}
