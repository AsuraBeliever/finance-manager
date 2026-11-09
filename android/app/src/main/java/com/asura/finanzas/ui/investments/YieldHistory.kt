package com.asura.finanzas.ui.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.InvestmentYield
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.PanelCard
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Days shown at first, and how many each «Ver más días» adds (web: DAYS_PAGE). */
private const val DAYS_PAGE = 30

/**
 * What an investment earned per month and per day (FIN-9) — the web's
 * `YieldHistory`. The worker computes and stores it; this only lays it out.
 */
@Composable
fun YieldHistorySection(
    repository: BrokeRepository,
    investmentId: Long,
    reloadKey: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val hide = settings.hideBalances
    var limit by remember(investmentId) { mutableStateOf(DAYS_PAGE) }
    // The last answer stays up while a longer one loads (web: keepPreviousData).
    var history by remember(investmentId) { mutableStateOf<InvestmentYield?>(null) }
    var loadedLimit by remember(investmentId) { mutableStateOf(0) }

    LaunchedEffect(investmentId, reloadKey, limit) {
        try {
            runCatching { repository.investmentYield(investmentId, limit) }
                .onSuccess { history = it }
        } finally {
            loadedLimit = limit
        }
    }

    val locale = remember(settings.locale) {
        if (settings.locale.startsWith("en")) Locale.US else Locale.forLanguageTag("es-MX")
    }
    // "noviembre de 2026" / "November 2026", first letter up as the web's CSS.
    val monthFormat = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale == Locale.US) "MMMM yyyy" else "MMMM 'de' yyyy", locale)
    }
    fun monthLabel(key: String): String = runCatching {
        YearMonth.parse(key).format(monthFormat).replaceFirstChar { it.titlecase(locale) }
    }.getOrDefault(key)

    val h = history
    fun signed(cents: Long): String =
        (if (cents < 0) "−" else "+") +
            maskIfHidden(formatMoney(kotlin.math.abs(cents), h?.currencyCode ?: "MXN"), hide)

    PanelCard(modifier) {
        Text(
            stringResource(R.string.investments_yield_history),
            style = MaterialTheme.typography.titleMedium,
            color = colors.fg,
        )
        Text(
            stringResource(
                if (h?.observed == true) R.string.investments_yield_observed_hint
                else R.string.investments_yield_history_hint,
            ),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = colors.fgSubtle,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (h == null) return@PanelCard
        if (h.days.isEmpty()) {
            Text(
                stringResource(R.string.investments_yield_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgSubtle,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@PanelCard
        }

        @Composable
        fun Heading(text: String) = Text(
            text.uppercase(locale),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = colors.fgSubtle,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        @Composable
        fun Line(label: String, cents: Long) = Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.fgMuted)
            Text(
                signed(cents),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (cents < 0) colors.danger else colors.accent,
            )
        }

        Heading(stringResource(R.string.investments_yield_by_month))
        Column(Modifier.padding(bottom = 16.dp)) {
            h.months.forEachIndexed { i, m ->
                if (i > 0) HairLine()
                Line(monthLabel(m.month), m.gainCents)
            }
        }
        Heading(stringResource(R.string.investments_yield_by_day))
        h.days.forEachIndexed { i, d ->
            if (i > 0) HairLine()
            Line(d.day, d.gainCents)
        }
        if (h.hasMore) {
            val loading = loadedLimit != limit
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                GhostButton(
                    stringResource(
                        if (loading) R.string.common_loading else R.string.investments_yield_show_more,
                    ),
                    onClick = { limit += DAYS_PAGE },
                    enabled = !loading,
                )
            }
        }
    }
}
