package com.asura.finanzas.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SimResult
import com.asura.finanzas.data.SolveResult
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.ChartLegend
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.ui.components.MultiLineChart
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.SimSeries
import com.asura.finanzas.ui.components.StackedAreaChart
import com.asura.finanzas.ui.components.chartColor
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke

/** The three things the web simulator answers. */
private enum class SimMode { Project, Goal, Compare }

private val CADENCES = listOf("monthly", "biweekly", "weekly", "none")

/** Percent typed as text -> basis points, the unit the API speaks. */
private fun rateToBps(text: String): Long? =
    text.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it >= 0 && it < 1000 }
        ?.let { Math.round(it * 100) }

private fun yearsToMonths(text: String): Int? =
    text.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it > 0 && it <= 100 }
        ?.let { Math.round(it * 12).toInt() }

/**
 * What-if projections. Nothing here touches the account: the inputs are typed,
 * and every peso of the answer is computed by `finanzas-core` on the server.
 *
 * Laid out as the web lays it out at phone width: the mode tabs, then the card
 * of inputs, then the answer — the stats, then the chart. The web puts the
 * inputs in a left column on a wide screen, but below `lg` that column stacks
 * on top, which is exactly this order.
 */
@Composable
fun SimulatorScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(SimMode.Project) }
    // These live here rather than in each mode so switching tabs keeps what was
    // typed, the way the web hoists them into SimulatorPage.
    var initial by remember { mutableStateOf("10000") }
    var contribution by remember { mutableStateOf("1000") }
    var cadence by remember { mutableStateOf("monthly") }
    var rate by remember { mutableStateOf("10") }
    var years by remember { mutableStateOf("5") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // The web titles this page with the question, not the word
            // "Simulator", and keeps a link back to investments above it.
            BackHeader(
                stringResource(R.string.simulator_subtitle),
                onBack,
                backLabel = stringResource(R.string.simulator_back),
            )
        }

        item {
            SegmentedControl(
                style = SegStyle.Modes,
                options = SimMode.entries,
                selected = mode,
                label = {
                    stringResource(
                        when (it) {
                            SimMode.Project -> R.string.simulator_mode_project
                            SimMode.Goal -> R.string.simulator_mode_goal
                            SimMode.Compare -> R.string.simulator_mode_compare
                        },
                    )
                },
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (mode) {
            SimMode.Project -> projectMode(
                repository, initial, { initial = it },
                contribution, { contribution = it },
                cadence, { cadence = it },
                rate, { rate = it },
                years, { years = it },
            )
            SimMode.Goal -> goalMode(
                repository, initial, { initial = it },
                rate, { rate = it },
                years, { years = it },
            )
            SimMode.Compare -> compareMode(
                repository, initial, { initial = it },
                contribution, { contribution = it },
                cadence, { cadence = it },
                years, { years = it },
            )
        }
    }
}

// ---- shared bits ----

/** A money input with the `$` and thousands grouping, as on the web. */
@Composable
private fun AmountField(labelRes: Int, value: String, onChange: (String) -> Unit) {
    MoneyField(
        label = stringResource(labelRes),
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A plain decimal input — the rate and the term are not money. */
@Composable
private fun PlainField(labelRes: Int, value: String, onChange: (String) -> Unit) {
    FormField(
        label = stringResource(labelRes),
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** The rate input with the web's three one-tap presets under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateField(rate: String, onRate: (String) -> Unit) {
    val colors = Broke.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlainField(R.string.simulator_rate, rate, onRate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nu" to "15", "CETES" to "10", "BONDDIA" to "6.5").forEach { (label, value) ->
                Text(
                    "$label $value%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(percent = 50))
                        .clickable { onRate(value) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CadenceField(cadence: String, onCadence: (String) -> Unit) {
    PickerField(
        label = stringResource(R.string.simulator_cadence),
        options = CADENCES,
        selected = cadence,
        optionLabel = { cadenceLabel(it) },
        onSelect = onCadence,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun cadenceLabel(cadence: String): String = stringResource(
    when (cadence) {
        "monthly" -> R.string.simulator_cadence_options_monthly
        "biweekly" -> R.string.simulator_cadence_options_biweekly
        "weekly" -> R.string.simulator_cadence_options_weekly
        else -> R.string.simulator_cadence_options_none
    },
)

/**
 * One of the three figures above the chart. The web stacks these full width
 * below its `sm` breakpoint, which is every phone.
 */
@Composable
private fun Stat(label: String, cents: Long?, color: Color) {
    val hide = LocalAppSettings.current.hideBalances
    GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = Broke.colors.fgSubtle,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            cents?.let { maskIfHidden(formatMoney(it), hide) } ?: "—",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
            color = color,
        )
    }
}

/** The `<h3>` at the top of a chart card. */
@Composable
private fun ChartTitle(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleLarge,
        color = Broke.colors.fg,
    )
    Spacer(Modifier.height(16.dp))
}

// ---- mode: projection ("¿cuánto crecería?") ----

private fun LazyListScope.projectMode(
    repository: BrokeRepository,
    initial: String,
    onInitial: (String) -> Unit,
    contribution: String,
    onContribution: (String) -> Unit,
    cadence: String,
    onCadence: (String) -> Unit,
    rate: String,
    onRate: (String) -> Unit,
    years: String,
    onYears: (String) -> Unit,
) {
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AmountField(R.string.simulator_initial, initial, onInitial)
                AmountField(R.string.simulator_contribution, contribution, onContribution)
                CadenceField(cadence, onCadence)
                RateField(rate, onRate)
                PlainField(R.string.simulator_years, years, onYears)
                // Rule of 72: a rough doubling time, as the web shows it.
                rate.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { pct ->
                    Text(
                        text(R.string.simulator_doubles_in, "years" to "%.1f".format(72.0 / pct)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Broke.colors.fgSubtle,
                    )
                }
            }
        }
    }

    item { ProjectResult(repository, initial, contribution, cadence, rate, years) }
}

@Composable
private fun ProjectResult(
    repository: BrokeRepository,
    initial: String,
    contribution: String,
    cadence: String,
    rate: String,
    years: String,
) {
    val colors = Broke.colors
    val initialCents = parseAmountToCents(initial) ?: 0
    val contributionCents = parseAmountToCents(contribution) ?: 0
    val bps = rateToBps(rate)
    val months = yearsToMonths(years)

    val result by produceState<SimResult?>(
        null, initialCents, contributionCents, cadence, bps, months,
    ) {
        value = if (bps == null || months == null) {
            value // keep the last answer while the input is half-typed
        } else {
            runCatching {
                repository.simulateInvestment(
                    initialCents, contributionCents, cadence, bps, months,
                )
            }.getOrDefault(value)
        }
    }

    val data = result
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat(stringResource(R.string.simulator_final_value), data?.finalValueCents, colors.accent)
        Stat(
            stringResource(R.string.simulator_total_contributed),
            data?.totalContributedCents,
            colors.fg,
        )
        Stat(
            stringResource(R.string.simulator_total_interest),
            data?.totalInterestCents,
            colors.cyan,
        )

        GlassCard(Modifier.fillMaxWidth()) {
            ChartTitle(R.string.simulator_growth_title)
            StackedAreaChart(
                contributed = data?.points?.map { it.contributedCents } ?: emptyList(),
                total = data?.points?.map { it.valueCents } ?: emptyList(),
                // Fixed hues from the shared chart palette, not the theme's
                // own cyan/green: the web draws these series from the same
                // constants, and they have to match across both.
                lowerColor = chartColor(2),
                upperColor = chartColor(4),
            )
            Spacer(Modifier.height(12.dp))
            ChartLegend(
                listOf(
                    chartColor(2) to stringResource(R.string.simulator_contributed_series),
                    chartColor(4) to stringResource(R.string.simulator_interest_series),
                ),
            )
        }
    }
}

// ---- mode: goal ("¿cuánto debo aportar para llegar a $X?") ----

private fun LazyListScope.goalMode(
    repository: BrokeRepository,
    initial: String,
    onInitial: (String) -> Unit,
    rate: String,
    onRate: (String) -> Unit,
    years: String,
    onYears: (String) -> Unit,
) {
    item {
        // The target belongs to this mode alone, so it lives with it — same as
        // the web, where GoalMode owns the state.
        var target by remember { mutableStateOf("100000") }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AmountField(R.string.simulator_goal_target, target) { target = it }
                    AmountField(R.string.simulator_initial, initial, onInitial)
                    RateField(rate, onRate)
                    PlainField(R.string.simulator_years, years, onYears)
                }
            }
            GoalResult(repository, initial, target, rate, years)
        }
    }
}

@Composable
private fun GoalResult(
    repository: BrokeRepository,
    initial: String,
    target: String,
    rate: String,
    years: String,
) {
    val hide = LocalAppSettings.current.hideBalances
    val initialCents = parseAmountToCents(initial) ?: 0
    val targetCents = parseAmountToCents(target) ?: 0
    val bps = rateToBps(rate)
    val months = yearsToMonths(years)

    val result by produceState<SolveResult?>(null, initialCents, targetCents, bps, months) {
        value = if (bps == null || months == null || targetCents <= 0) {
            value
        } else {
            runCatching {
                repository.solveContribution(initialCents, targetCents, bps, months)
            }.getOrDefault(value)
        }
    }

    val monthly = result?.monthlyContributionCents
    GlassCard(Modifier.fillMaxWidth(), padding = 24.dp) {
        Text(
            stringResource(R.string.simulator_goal_need),
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            monthly?.let { maskIfHidden(formatMoney(it), hide) } ?: "—",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp),
            color = Broke.colors.accent,
        )
        if (monthly == 0L) {
            // The starting amount already gets there on its own.
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.simulator_goal_reached),
                style = MaterialTheme.typography.bodyMedium,
                color = Broke.colors.fgSubtle,
            )
        }
    }
}

// ---- mode: compare ----

/** One row of the comparison: a name, an editable rate, and its colour. */
private data class Instrument(val key: String, val name: String, val rate: String, val color: Color)

private fun LazyListScope.compareMode(
    repository: BrokeRepository,
    initial: String,
    onInitial: (String) -> Unit,
    contribution: String,
    onContribution: (String) -> Unit,
    cadence: String,
    onCadence: (String) -> Unit,
    years: String,
    onYears: (String) -> Unit,
) {
    item {
        var instruments by remember {
            mutableStateOf(
                listOf(
                    Instrument("nu", "Nu", "15", chartColor(0)),
                    Instrument("cetes", "CETES", "10", chartColor(1)),
                    Instrument("bonddia", "BONDDIA", "6.5", chartColor(2)),
                ),
            )
        }

        // Fill in the real Banxico rates once, when the catalog arrives.
        val catalog by produceState(initialValue = null as Map<String, Long?>?) {
            value = runCatching {
                repository.investmentCatalog().associate { it.id to it.rateBps }
            }.getOrNull()
        }
        LaunchedEffect(catalog) {
            val rates = catalog ?: return@LaunchedEffect
            instruments = instruments.map { inst ->
                val live = when (inst.key) {
                    "cetes" -> rates["cetes_91"]
                    "bonddia" -> rates["bonddia"]
                    else -> null
                }
                if (live == null) inst else inst.copy(rate = (live / 100.0).trimmed())
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AmountField(R.string.simulator_initial, initial, onInitial)
                    AmountField(R.string.simulator_contribution, contribution, onContribution)
                    CadenceField(cadence, onCadence)
                    PlainField(R.string.simulator_years, years, onYears)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.simulator_presets),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = Broke.colors.fgMuted,
                        )
                        instruments.forEachIndexed { index, inst ->
                            InstrumentRow(inst) { typed ->
                                instruments = instruments.mapIndexed { i, p ->
                                    if (i == index) p.copy(rate = typed) else p
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.simulator_compare_hint),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = Broke.colors.fgSubtle,
                        )
                    }
                }
            }
            CompareResult(repository, instruments, initial, contribution, cadence, years)
        }
    }
}

/** "15" rather than "15.0" — the web builds these from the raw basis points. */
private fun Double.trimmed(): String =
    if (this == Math.floor(this)) toLong().toString() else toString()

@Composable
private fun InstrumentRow(inst: Instrument, onRate: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(inst.color),
        )
        Text(
            inst.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fg,
            modifier = Modifier.padding(horizontal = 8.dp).width(72.dp),
            maxLines = 1,
        )
        FormField(
            label = "",
            value = inst.rate,
            onValueChange = onRate,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(
            "%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = Broke.colors.fgSubtle,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun CompareResult(
    repository: BrokeRepository,
    instruments: List<Instrument>,
    initial: String,
    contribution: String,
    cadence: String,
    years: String,
) {
    val hide = LocalAppSettings.current.hideBalances
    val initialCents = parseAmountToCents(initial) ?: 0
    val contributionCents = parseAmountToCents(contribution) ?: 0
    val months = yearsToMonths(years)
    val ratesKey = instruments.joinToString(",") { it.rate }

    val results by produceState<List<Pair<Instrument, SimResult>>>(
        emptyList(), initialCents, contributionCents, cadence, months, ratesKey,
    ) {
        value = if (months == null) {
            value
        } else {
            instruments.mapNotNull { inst ->
                val bps = rateToBps(inst.rate) ?: return@mapNotNull null
                runCatching {
                    repository.simulateInvestment(
                        initialCents, contributionCents, cadence, bps, months,
                    )
                }.getOrNull()?.let { inst to it }
            }.ifEmpty { value }
        }
    }

    val series = results
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { (inst, sim) ->
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(inst.color),
                    )
                    Text(
                        "${inst.name} · ${inst.rate}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Broke.colors.fgSubtle,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    maskIfHidden(formatMoney(sim.finalValueCents), hide),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp),
                    color = Broke.colors.fg,
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            ChartTitle(R.string.simulator_compare_title)
            MultiLineChart(
                series.map { (inst, sim) ->
                    SimSeries(inst.name, inst.color, sim.points.map { it.valueCents })
                },
            )
        }
    }
}
