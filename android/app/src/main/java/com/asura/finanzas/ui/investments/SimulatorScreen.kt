package com.asura.finanzas.ui.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.CatalogItem
import com.asura.finanzas.data.SimResult
import com.asura.finanzas.data.SolveResult
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.LineChart
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegmentedControl
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
 */
@Composable
fun SimulatorScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(SimMode.Project) }
    var initial by remember { mutableStateOf("10000") }
    var contribution by remember { mutableStateOf("1000") }
    var cadence by remember { mutableStateOf("monthly") }
    var rate by remember { mutableStateOf("10") }
    var years by remember { mutableStateOf("5") }
    var target by remember { mutableStateOf("100000") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.simulator_title), onBack)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.simulator_description),
                style = MaterialTheme.typography.bodySmall,
                color = Broke.colors.fgSubtle,
            )
        }

        item {
            SegmentedControl(
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

        // Shared inputs. Which ones matter depends on the mode, same as the web.
        item {
            AmountField(R.string.simulator_initial, initial) { initial = it }
        }
        // Contribution and cadence drive both the projection and the comparison.
        if (mode != SimMode.Goal) {
            item { AmountField(R.string.simulator_contribution, contribution) { contribution = it } }
            item {
                PickerField(
                    label = stringResource(R.string.simulator_cadence),
                    options = CADENCES,
                    selected = cadence,
                    optionLabel = { cadenceLabel(it) },
                    onSelect = { cadence = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (mode == SimMode.Goal) {
            item { AmountField(R.string.simulator_goal_target, target) { target = it } }
        }
        if (mode != SimMode.Compare) {
            item { NumberField(R.string.simulator_rate, rate) { rate = it } }
        }
        item { NumberField(R.string.simulator_years, years) { years = it } }

        when (mode) {
            SimMode.Project -> item {
                ProjectResult(repository, initial, contribution, cadence, rate, years)
            }
            SimMode.Goal -> item { GoalResult(repository, initial, target, rate, years) }
            SimMode.Compare -> item {
                CompareResult(repository, initial, contribution, cadence, years)
            }
        }
    }
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

@Composable
private fun AmountField(labelRes: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(labelRes: Int, value: String, onChange: (String) -> Unit) =
    AmountField(labelRes, value, onChange)

/** "Proyección": what the plan grows into, with the curve and the split. */
@Composable
private fun ProjectResult(
    repository: BrokeRepository,
    initial: String,
    contribution: String,
    cadence: String,
    rate: String,
    years: String,
) {
    val hide = LocalAppSettings.current.hideBalances
    val initialCents = parseAmountToCents(initial) ?: 0
    val contributionCents = parseAmountToCents(contribution) ?: 0
    val bps = rateToBps(rate)
    val months = yearsToMonths(years)

    val result by produceState<SimResult?>(
        null, initialCents, contributionCents, cadence, bps, months,
    ) {
        value = if (bps == null || months == null) {
            null
        } else {
            runCatching {
                repository.simulateInvestment(
                    initialCents, contributionCents, cadence, bps, months,
                )
            }.getOrNull()
        }
    }

    val data = result ?: return
    GlassCard(Modifier.fillMaxWidth()) {
        MicroLabel(stringResource(R.string.simulator_growth_title))
        Spacer(Modifier.height(10.dp))
        LineChart(
            values = data.points.map { it.valueCents },
            startLabel = "0",
            endLabel = years,
            minLabel = maskIfHidden(formatMoney(data.points.minOfOrNull { it.valueCents } ?: 0), hide),
            maxLabel = maskIfHidden(formatMoney(data.finalValueCents), hide),
        )
        Spacer(Modifier.height(12.dp))
        HairLine()
        Spacer(Modifier.height(12.dp))
        ResultLine(R.string.simulator_final_value, data.finalValueCents, hide, hero = true)
        ResultLine(R.string.simulator_total_contributed, data.totalContributedCents, hide)
        ResultLine(R.string.simulator_total_interest, data.totalInterestCents, hide)

        // Rule of 72: a rough doubling time, shown exactly as the web does.
        rate.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { pct ->
            Spacer(Modifier.height(10.dp))
            Text(
                text(R.string.simulator_doubles_in, "years" to "%.1f".format(72.0 / pct)),
                style = MaterialTheme.typography.labelSmall,
                color = Broke.colors.fgSubtle,
            )
        }
    }
}

/** "Meta": how much per month to reach a target. */
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
            null
        } else {
            runCatching {
                repository.solveContribution(initialCents, targetCents, bps, months)
            }.getOrNull()
        }
    }

    val data = result ?: return
    GlassCard(Modifier.fillMaxWidth()) {
        if (data.monthlyContributionCents <= 0) {
            // The starting amount already gets there on its own.
            Text(
                stringResource(R.string.simulator_goal_reached),
                style = MaterialTheme.typography.bodyMedium,
                color = Broke.colors.positive,
            )
        } else {
            ResultLine(
                R.string.simulator_goal_need,
                data.monthlyContributionCents,
                hide,
                hero = true,
            )
        }
    }
}

/** "Comparar": the same plan under each catalog instrument's own rate. */
@Composable
private fun CompareResult(
    repository: BrokeRepository,
    initial: String,
    contribution: String,
    cadence: String,
    years: String,
) {
    val hide = LocalAppSettings.current.hideBalances
    val initialCents = parseAmountToCents(initial) ?: 0
    val contributionCents = parseAmountToCents(contribution) ?: 0
    val months = yearsToMonths(years)

    val catalog by produceState(initialValue = emptyList<CatalogItem>()) {
        value = runCatching { repository.investmentCatalog() }.getOrDefault(emptyList())
    }
    // Only the instruments whose rate the server actually knows can be compared.
    val rated = catalog.filter { it.rateBps != null }

    val results by produceState(
        initialValue = emptyMap<String, Long>(),
        rated.map { it.id }.joinToString(),
        initialCents,
        contributionCents,
        cadence,
        months,
    ) {
        value = if (months == null) {
            emptyMap()
        } else {
            rated.mapNotNull { item ->
                val bps = item.rateBps ?: return@mapNotNull null
                runCatching {
                    repository.simulateInvestment(
                        initialCents, contributionCents, cadence, bps, months,
                    )
                }.getOrNull()?.let { item.id to it.finalValueCents }
            }.toMap()
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        MicroLabel(stringResource(R.string.simulator_compare_title))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.simulator_compare_hint),
            style = MaterialTheme.typography.labelSmall,
            color = Broke.colors.fgSubtle,
        )
        Spacer(Modifier.height(10.dp))
        // Best first, so the comparison reads at a glance.
        rated.sortedByDescending { results[it.id] ?: 0 }.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        catalogName(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Broke.colors.fg,
                    )
                    Text(
                        "%.2f%%".format((item.rateBps ?: 0) / 100.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = Broke.colors.fgSubtle,
                    )
                }
                Text(
                    results[item.id]?.let { maskIfHidden(formatMoney(it), hide) } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Broke.colors.fg,
                )
            }
        }
    }
}

@Composable
private fun ResultLine(labelRes: Int, cents: Long, hide: Boolean, hero: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
        )
        Text(
            maskIfHidden(formatMoney(cents), hide),
            style = if (hero) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (hero) Broke.colors.accent else Broke.colors.fg,
        )
    }
}
