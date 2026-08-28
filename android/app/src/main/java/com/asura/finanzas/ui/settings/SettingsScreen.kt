package com.asura.finanzas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.R
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.ThemeChoice
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.SettingRow
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

private enum class Locale(val label: String, val tag: String) {
    Spanish("Español", "es"),
    English("English", "en"),
}

@Composable
fun SettingsScreen(
    repository: BrokeRepository,
    preferences: AppPreferences,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    var showCurrencies by remember { mutableStateOf(false) }

    // The device's zones, with the current pick first so it is always listable —
    // the same guarantee `listTimezones()` makes on the web.
    val timezones = remember(settings.timezone) {
        (listOf(settings.timezone, java.util.TimeZone.getDefault().id) +
            java.util.TimeZone.getAvailableIDs().filter { it.contains('/') }.sorted())
            .distinct()
    }

    if (showCurrencies) {
        CurrenciesScreen(
            repository = repository,
            onBack = { showCurrencies = false },
            modifier = modifier,
        )
        return
    }

    val onOpenCurrencies = { showCurrencies = true }

    val themeIcon: (ThemeChoice) -> ImageVector = {
        when (it) {
            ThemeChoice.Light -> Icons.Outlined.LightMode
            ThemeChoice.Dark -> Icons.Outlined.DarkMode
            ThemeChoice.System -> Icons.Outlined.Monitor
        }
    }
    val themeLabel: @Composable (ThemeChoice) -> String = {
        stringResource(
            when (it) {
                ThemeChoice.Light -> R.string.theme_light
                ThemeChoice.Dark -> R.string.theme_dark
                ThemeChoice.System -> R.string.theme_system
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(stringResource(R.string.settings_title))

        GlassCard(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.theme_label)) {
                SegmentedControl(
                    // Web order: Light, Dark, Auto.
                    options = listOf(ThemeChoice.Light, ThemeChoice.Dark, ThemeChoice.System),
                    selected = settings.theme,
                    label = { themeLabel(it) },
                    icon = themeIcon,
                    onSelect = { scope.launch { preferences.setTheme(it) } },
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_language)) {
                SegmentedControl(
                    options = Locale.entries,
                    selected = Locale.entries.first { it.tag == settings.locale },
                    label = { it.label },
                    onSelect = { scope.launch { preferences.setLocale(it.tag) } },
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_timezone),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_timezone_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
            Spacer(Modifier.height(12.dp))
            PickerField(
                label = stringResource(R.string.settings_timezone),
                options = timezones,
                selected = settings.timezone,
                optionLabel = { it },
                onSelect = { scope.launch { preferences.setTimezone(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_clock)) {
                SegmentedControl(
                    options = listOf(false, true),
                    selected = settings.clock24,
                    label = {
                        stringResource(if (it) R.string.settings_clock24 else R.string.settings_clock12)
                    },
                    onSelect = { scope.launch { preferences.setClock24(it) } },
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.dashboard_hide_balance),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Switch(
                    checked = settings.hideBalances,
                    onCheckedChange = { scope.launch { preferences.setHideBalances(it) } },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.surface,
                    ),
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth().clickable { onOpenCurrencies() }) {
            Text(
                stringResource(R.string.settings_currencies),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_currencies_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${stringResource(R.string.settings_version)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
            Text(
                BuildConfig.API_BASE,
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }

        OutlinedButton(
            onClick = { scope.launch { repository.logout(); onSignedOut() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.auth_logout), color = colors.danger)
        }
    }
}
