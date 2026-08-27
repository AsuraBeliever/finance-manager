package com.asura.finanzas.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parse what someone typed into integer cents. This is input parsing, not money
 * arithmetic: the value goes straight to the server, which does every actual
 * calculation. BigDecimal (never Double) so "0.29" can't land as 28 cents.
 *
 * Accepts "1234.56", "1,234.56" and "1 234,56" — people paste amounts from
 * their bank app in whichever form it used.
 */
fun parseAmountToCents(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val cleaned = trimmed
        .replace(" ", "")
        .replace(" ", "")
        .replace(Regex("[^0-9.,-]"), "")

    val lastDot = cleaned.lastIndexOf('.')
    val lastComma = cleaned.lastIndexOf(',')
    // Whichever separator comes last is the decimal one; the other groups digits.
    val normalized = when {
        lastDot >= 0 && lastComma >= 0 ->
            if (lastDot > lastComma) cleaned.replace(",", "")
            else cleaned.replace(".", "").replace(',', '.')
        lastComma >= 0 -> cleaned.replace(',', '.')
        else -> cleaned
    }

    val decimal = normalized.toBigDecimalOrNull() ?: return null
    if (decimal.signum() < 0) return null
    return decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
