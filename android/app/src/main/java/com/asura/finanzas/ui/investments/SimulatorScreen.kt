package com.asura.finanzas.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import com.asura.finanzas.ui.components.cssLineBox
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular

/** The three things the web simulator answers. */
private enum class SimMode { Project, Goal, Compare }

private val CADENCES = listOf("monthly", "biweekly", "weekly", "none")

/** Percent typed as text -> basis points, the unit the API speaks. */
/**
 * JavaScript's `Number(s)`, which the web parses these boxes with: blank is 0,
 * anything unparsable is NaN (and the web then falls back to 0).
 */
private fun jsNumber(text: String): Double? =
    text.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }?.takeIf { it.isFinite() }

/** The web's `rateToBps`: a percentage to basis points, 0 when unreadable. */
private fun rateToBps(text: String): Long = jsNumber(text)?.let { Math.round(it * 100) } ?: 0

/** The web's `yearsToMonths`: 0 when unreadable. */
private fun yearsToMonths(text: String): Int = jsNumber(text)?.let { Math.round(it * 12).toInt() } ?: 0

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // The web titles this page with the question, not the word
            // "Simulator", and keeps a link back to investments above it.
            // The header's `mb-7`: 28, of which the list's gap gives 16.
            BackHeader(
                stringResource(R.string.simulator_subtitle),
                onBack,
                Modifier.padding(bottom = 12.dp),
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
                // `inline-flex mb-5`: as wide as its three options.
                modifier = Modifier.padding(bottom = 4.dp),
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
    // A fragment in the form's `grid gap-4`: the presets sit 16 under it.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlainField(R.string.simulator_rate, rate, onRate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nu" to "15", "CETES" to "10", "BONDDIA" to "6.5").forEach { (label, value) ->
                Text(
                    "$label $value%",
                    // `rounded-full border px-3 py-1 text-xs`.
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(percent = 50))
                        .clickable { onRate(value) }
                        .padding(horizontal = 13.dp, vertical = 5.dp),
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
            style = MaterialTheme.typography.bodySmall,
            color = Broke.colors.fgSubtle,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // Hypothetical money: the web prints it even in privacy mode.
            cents?.let { formatMoney(it) } ?: "—",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, lineHeight = 28.sp).tabular(),
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
                jsNumber(rate)?.takeIf { it > 0 }?.let { pct ->
                    Text(
                        text(
                            R.string.simulator_doubles_in,
                            "years" to java.math.BigDecimal(72.0 / pct).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Broke.colors.fgSubtle,
                    )
                }
            }
        }
    }

    // `grid gap-5` between the form and the results.
    item { Box(Modifier.padding(top = 4.dp)) { ProjectResult(repository, initial, contribution, cadence, rate, years) } }
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
        // The web only asks while 0 < months <= 1200, keeping the last answer.
        value = if (months !in 1..1200) {
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
    // `space-y-4` around a `grid gap-3` of the three figures.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

        GlassCard(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            ChartTitle(R.string.simulator_growth_title)
            StackedAreaChart(
                contributed = data?.points?.map { it.contributedCents } ?: emptyList(),
                total = data?.points?.map { it.valueCents } ?: emptyList(),
                // Fixed hues from the shared chart palette, not the theme's
                // own cyan/green: the web draws these series from the same
                // constants, and they have to match across both.
                lowerColor = chartColor(2),
                upperColor = chartColor(4),
                interest = data?.points?.map { it.interestCents } ?: emptyList(),
                names = stringResource(R.string.simulator_contributed_series) to
                    stringResource(R.string.simulator_interest_series),
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

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
        value = if (months <= 0 || targetCents <= 0) {
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
            monthly?.let { formatMoney(it) } ?: "—",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp, lineHeight = 40.sp).tabular(),
            color = Broke.colors.accent,
            modifier = Modifier.cssLineBox(40.dp),
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

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AmountField(R.string.simulator_initial, initial, onInitial)
                    AmountField(R.string.simulator_contribution, contribution, onContribution)
                    CadenceField(cadence, onCadence)
                    PlainField(R.string.simulator_years, years, onYears)

                    // `space-y-2 pt-1`, captioned like a field label.
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.simulator_presets),
                            style = MaterialTheme.typography.labelMedium,
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
                            style = MaterialTheme.typography.bodySmall,
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
    // `flex items-center gap-2`: swatch, a `w-20` name, the rate box, "%".
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
            style = MaterialTheme.typography.bodySmall,
            color = Broke.colors.fgSubtle,
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
        value = if (months !in 1..1200) {
            value
        } else {
            instruments.mapNotNull { inst ->
                val bps = rateToBps(inst.rate)
                runCatching {
                    repository.simulateInvestment(
                        initialCents, contributionCents, cadence, bps, months,
                    )
                }.getOrNull()?.let { inst to it }
            }.ifEmpty { value }
        }
    }

    val series = results
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        style = MaterialTheme.typography.bodySmall,
                        color = Broke.colors.fgSubtle,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatMoney(sim.finalValueCents),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp, lineHeight = 28.sp).tabular(),
                    color = Broke.colors.fg,
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            ChartTitle(R.string.simulator_compare_title)
            MultiLineChart(
                series.map { (inst, sim) ->
                    SimSeries(inst.name, inst.color, sim.points.map { it.valueCents })
                },
            )
        }
    }
}
