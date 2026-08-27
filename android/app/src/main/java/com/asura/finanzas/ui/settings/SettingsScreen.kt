package com.asura.finanzas.ui.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.R
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.ThemeChoice
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.SectionTitle
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_title))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Español", settings.locale == "es") {
                    scope.launch { preferences.setLocale("es") }
                }
                Choice("English", settings.locale == "en") {
                    scope.launch { preferences.setLocale("en") }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.theme_label),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.theme_system), settings.theme == ThemeChoice.System) {
                    scope.launch { preferences.setTheme(ThemeChoice.System) }
                }
                Choice(stringResource(R.string.theme_light), settings.theme == ThemeChoice.Light) {
                    scope.launch { preferences.setTheme(ThemeChoice.Light) }
                }
                Choice(stringResource(R.string.theme_dark), settings.theme == ThemeChoice.Dark) {
                    scope.launch { preferences.setTheme(ThemeChoice.Dark) }
                }
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.fg,
                )
                Switch(
                    checked = settings.hideBalances,
                    onCheckedChange = { scope.launch { preferences.setHideBalances(it) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_version),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg,
            )
            Spacer(Modifier.height(4.dp))
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

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Broke.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent,
            selectedLabelColor = colors.surface,
        ),
    )
}
