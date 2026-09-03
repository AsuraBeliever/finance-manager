package com.asura.finanzas.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.OutlineButton
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Investment
import com.asura.finanzas.data.Portfolio
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.DonutSlice
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.components.chartColor
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke

@Composable
fun InvestmentsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    var showClosed by remember { mutableStateOf(false) }
    val state by loadSynced("investments" to showClosed, refetch = key) {
        repository.investments(includeClosed = showClosed)
    }
    val portfolioState by loadSynced("portfolio", refetch = key) { repository.portfolio() }
    val portfolio = (portfolioState as? Load.Ready)?.data

    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var simulating by remember { mutableStateOf(false) }

    if (simulating) {
        SimulatorScreen(
            repository = repository,
            onBack = { simulating = false },
            modifier = modifier,
        )
        return
    }

    val id = openId
    if (id != null) {
        InvestmentDetailScreen(
            repository = repository,
            investmentId = id,
            onBack = { openId = null; reload() },
            modifier = modifier,
        )
        return
    }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> InvestmentList(
            investments = current.data,
            portfolio = portfolio,
            fromCache = current.fromCache,
            showClosed = showClosed,
            onToggleClosed = { showClosed = !showClosed },
            onNew = { creating = true },
            onOpen = { openId = it.id },
            onSimulator = { simulating = true },
            modifier = modifier,
        )
    }

    if (creating) {
        NewInvestmentSheet(
            repository = repository,
            onDismiss = { creating = false },
            onSaved = { creating = false; reload() },
        )
    }
}

@Composable
private fun InvestmentList(
    investments: List<Investment>,
    portfolio: Portfolio?,
    fromCache: Boolean,
    showClosed: Boolean,
    onToggleClosed: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Investment) -> Unit,
    onSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val shown = if (showClosed) investments else investments.filter { !it.isClosed }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // Same order and shapes as the web: eye, the "show closed" checkbox,
            // the outlined simulator link, then the primary action.
            PageHeader(stringResource(R.string.investments_title)) {
                PrivacyToggle()
                // Four controls do not fit this width laid out at their natural
                // size. In the browser they are flex children, so they shrink
                // and let their labels run onto a second line rather than
                // pushing the last one down to a row of its own.
                //
                // `IntrinsicSize.Min` is that behaviour exactly: a flex item
                // will not shrink past its min-content width, which for a label
                // is its longest word. Hand-picked `weight` fractions used to
                // stand in for this, and being a few dp short left the primary
                // action reading "New investmen".
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .clickable { onToggleClosed() },
                ) {
                    WebCheckbox(
                        checked = showClosed,
                        onCheckedChange = { onToggleClosed() },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.investments_show_closed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                }
                OutlineButton(
                    text = stringResource(R.string.simulator_open),
                    onClick = onSimulator,
                    leadingIcon = Lucide.Calculator,
                    modifier = Modifier.width(IntrinsicSize.Min),
                )
                PrimaryButton(
                    text = stringResource(R.string.investments_new_investment),
                    onClick = onNew,
                    leadingIcon = Lucide.Plus,
                    modifier = Modifier.width(IntrinsicSize.Min),
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (portfolio != null) {
            item { PortfolioCard(portfolio, hide) }
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.investments_empty_title),
                    stringResource(R.string.investments_empty_description),
                )
            }
        }

        items(shown, key = { it.id }) { investment -> InvestmentCard(investment, hide, onOpen) }
    }
}

/**
 * The web's portfolio summary: four figures in a 2×2 grid over a donut of the
 * open positions. Every number is the server's; the annualised return in
 * particular is finanzas-core's XIRR, never recomputed here.
 */
@Composable
private fun PortfolioCard(portfolio: Portfolio, hide: Boolean) {
    val colors = Broke.colors

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Stat(
                stringResource(R.string.investments_portfolio_value),
                maskIfHidden(formatMoney(portfolio.totalValueCents), hide),
                colors.accent,
                Modifier.weight(1f),
            )
            Stat(
                stringResource(R.string.investments_portfolio_invested),
                maskIfHidden(formatMoney(portfolio.totalInvestedCents), hide),
                colors.fg,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat(
                stringResource(R.string.investments_portfolio_gain),
                maskIfHidden(formatMoney(portfolio.totalGainCents), hide),
                if (portfolio.totalGainCents >= 0) colors.positive else colors.danger,
                Modifier.weight(1f),
            )
            Stat(
                stringResource(R.string.investments_annualized_return),
                portfolio.annualizedReturnBps
                    // Basis points to a percentage is presentation only.
                    ?.let { (if (it >= 0) "+" else "") + "%.1f%%".format(it / 100.0) }
                    ?: "—",
                if ((portfolio.annualizedReturnBps ?: 0) >= 0) colors.positive else colors.danger,
                Modifier.weight(1f),
            )
        }

        if (portfolio.slices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            // A small ring beside its key, the way the web draws it — not a big
            // centred donut with the total in the hole.
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniDonut(
                    slices = portfolio.slices.map { it.currentValueCents },
                    modifier = Modifier.size(112.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The web lists at most five, truncated.
                    portfolio.slices.take(5).forEachIndexed { index, slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(chartColor(index)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                slice.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = colors.fgMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Broke.colors.fgMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
            color = valueColor,
        )
    }
}

/**
 * Name, value, gain, then the calculator and maturity as a caption — the web's
 * card, which leads with the money rather than the currency code.
 */
@Composable
private fun InvestmentCard(investment: Investment, hide: Boolean, onOpen: (Investment) -> Unit) {
    val colors = Broke.colors

    GlassCard(Modifier.fillMaxWidth().clickable { onOpen(investment) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                investment.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // A closed investment wears a pill up here, not a word appended to
            // the caption — the web puts it at the end of the name's row.
            if (investment.isClosed) {
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.investments_closed),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceOverlay)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            maskIfHidden(formatMoney(investment.currentValueCents, investment.currencyCode), hide),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.fg,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                // Losing money points down, as on the web; the same arrow in
                // red read as a gain at a glance.
                if (investment.gainCents >= 0) Lucide.TrendingUp else Lucide.TrendingDown,
                contentDescription = null,
                tint = if (investment.gainCents >= 0) colors.accent else colors.danger,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                maskIfHidden(formatDelta(investment.gainCents, investment.currencyCode), hide),
                style = MaterialTheme.typography.bodyLarge,
                color = if (investment.gainCents >= 0) colors.accent else colors.danger,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                // A crypto holding says how much of the coin it is, not that it
                // is crypto — the amount is the useful part (web: `cryptoSub`).
                if (investment.calculator == "crypto") {
                    cryptoHolding(investment.paramsJson) ?: calculatorLabel(investment.calculator)
                } else {
                    calculatorLabel(investment.calculator)
                },
                investment.maturityDate?.let {
                    "${stringResource(R.string.investments_maturity)}: $it"
                },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
    }
}

/**
 * "0.05 BTC" out of a crypto holding's stored params, or null when they do not
 * parse. Quantity is a count, not money — no cents arithmetic here.
 */
private fun cryptoHolding(paramsJson: String): String? = runCatching {
    val params = Json.parseToJsonElement(paramsJson).jsonObject
    val quantity = params["quantity_e8"]!!.jsonPrimitive.long
    val symbol = params["symbol"]!!.jsonPrimitive.content
    val whole = quantity / 100_000_000
    val frac = (quantity % 100_000_000).toString().padStart(8, '0').trimEnd('0')
    (if (frac.isEmpty()) "$whole" else "$whole.$frac") + " " + symbol
}.getOrNull()

/** The calculator's display name, from the shared dictionary. */
@Composable
private fun calculatorLabel(calculator: String): String = stringResource(
    when (calculator) {
        "nu_cajita" -> R.string.investments_calculators_nu_cajita
        "cetes" -> R.string.investments_calculators_cetes
        "bonddia" -> R.string.investments_calculators_bonddia
        "crypto" -> R.string.investments_calculators_crypto
        "fixed_rate" -> R.string.investments_calculators_fixed_rate
        else -> R.string.investments_calculators_manual
    },
)


/**
 * The portfolio ring: 112 dp across with a 34→56 radius and a 2° gap between
 * slices, matching the web's chart exactly. No labels — the key sits beside it.
 */
@Composable
private fun MiniDonut(slices: List<Long>, modifier: Modifier = Modifier) {
    val total = slices.sum().coerceAtLeast(1)
    val colors = slices.indices.map { chartColor(it) }
    Canvas(modifier) {
        val stroke = (56f - 34f) / 56f * (size.minDimension / 2f)
        val radius = size.minDimension / 2f - stroke / 2f
        // Recharts measures angles the way maths does — anticlockwise from three
        // o'clock — and `Pie` defaults to startAngle 0. Compose's drawArc is the
        // mirror of that (clockwise, same origin), hence the negated angles.
        var start = 0f
        slices.forEachIndexed { index, value ->
            val sweep = 360f * (value.toFloat() / total.toFloat())
            drawArc(
                color = colors[index],
                startAngle = -(start + sweep) + 1f,
                sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                useCenter = false,
                topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}
