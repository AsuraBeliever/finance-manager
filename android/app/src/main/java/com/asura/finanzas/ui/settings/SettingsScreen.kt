package com.asura.finanzas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.ui.components.Lucide
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.AppearanceSync
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SessionInfo
import com.asura.finanzas.data.ThemeChoice
import com.asura.finanzas.data.User
import com.asura.finanzas.data.WalletCategory
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.SettingRow
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

private enum class Locale(val label: String, val tag: String) {
    Spanish("Español", "es"),
    English("English", "en"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: BrokeRepository,
    preferences: AppPreferences,
    appearanceSync: AppearanceSync,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    var showCurrencies by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var reloadSessions by remember { mutableStateOf(0) }

    val user by produceState<User?>(null) { value = runCatching { repository.me() }.getOrNull() }
    val walletCategories by produceState<List<WalletCategory>>(emptyList()) {
        value = runCatching { repository.walletCategories() }.getOrDefault(emptyList())
    }
    val sessions by produceState<List<SessionInfo>>(emptyList(), reloadSessions) {
        value = runCatching { repository.sessions() }.getOrDefault(emptyList())
    }

    // The device's zones, with the current pick first so it is always listable —
    // the same guarantee `listTimezones()` makes on the web.
    val timezones = remember(settings.timezone) {
        (listOf(settings.timezone, java.util.TimeZone.getDefault().id) +
            java.util.TimeZone.getAvailableIDs().filter { it.contains('/') }.sorted())
            .distinct()
    }

    if (showAppearance) {
        AppearanceScreen(
            sync = appearanceSync,
            onBack = { showAppearance = false },
            modifier = modifier,
        )
        return
    }

    if (showCurrencies) {
        CurrenciesScreen(
            repository = repository,
            onBack = { showCurrencies = false },
            modifier = modifier,
        )
        return
    }

    if (showWhatsNew) {
        WhatsNewScreen(onBack = { showWhatsNew = false }, modifier = modifier)
        return
    }

    if (showPassword) {
        ChangePasswordScreen(
            repository = repository,
            onBack = { showPassword = false },
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
                    style = SegStyle.Theme,
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
                    style = SegStyle.Tray,
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
            // The web's select sits bare under the hint — no second label.
            PickerField(
                label = "",
                options = timezones,
                selected = settings.timezone,
                // The web prints zone ids with spaces, not underscores.
                optionLabel = { it.replace('_', ' ') },
                onSelect = { scope.launch { preferences.setTimezone(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            // Clock format lives inside this card on the web, not in one of its
            // own; and there is no "hide balance" row at all — the eye in the
            // headers is the control, on both surfaces.
            Spacer(Modifier.height(16.dp))
            SettingRow(stringResource(R.string.settings_clock), subtle = true) {
                SegmentedControl(
                    style = SegStyle.Tray,
                    options = listOf(false, true),
                    selected = settings.clock24,
                    label = {
                        stringResource(if (it) R.string.settings_clock24 else R.string.settings_clock12)
                    },
                    onSelect = { scope.launch { preferences.setClock24(it) } },
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth().clickable { showAppearance = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Lucide.Palette,
                    contentDescription = null,
                    tint = colors.fgSubtle,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.appearance_settings_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.appearance_manage),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = colors.accent,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Lucide.ChevronRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
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

        if (walletCategories.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_wallet_categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    walletCategories.forEach { category ->
                        Text(
                            seedName(category.name, category.isSystem).orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.fgMuted,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceOverlay)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.account_devices),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Spacer(Modifier.height(6.dp))
                sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onRevoke = {
                            scope.launch {
                                runCatching { repository.revokeSession(session.id) }
                                reloadSessions++
                            }
                        },
                    )
                }
                if (sessions.count { !it.current } > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.account_revoke_others),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.danger,
                        modifier = Modifier.clickable {
                            scope.launch {
                                runCatching { repository.revokeOtherSessions() }
                                reloadSessions++
                            }
                        },
                    )
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                modifier = Modifier.clickable { showWhatsNew = true },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.whats_new_settings_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
                modifier = Modifier.clickable { showWhatsNew = true },
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.whats_new_notify_on_update),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.changelogEnabled,
                    onCheckedChange = {
                        scope.launch { preferences.setChangelogEnabled(it) }
                    },
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth().clickable { showPassword = true }) {
            Text(
                stringResource(R.string.account_change_password),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.account_password_settings_hint),
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

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_session),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    user?.email.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.fgMuted,
                )
                Text(
                    stringResource(R.string.auth_logout),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.danger,
                    modifier = Modifier.clickable {
                        scope.launch { repository.logout(); onSignedOut() }
                    },
                )
            }
        }
    }
}

/** One signed-in device, with its own sign-out unless it is this one. */
@Composable
private fun SessionRow(session: SessionInfo, onRevoke: () -> Unit) {
    val colors = Broke.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.userAgent?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.account_unknown_device),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
            )
            Text(
                session.lastSeenAt?.let {
                    "${stringResource(R.string.account_last_seen)} $it"
                } ?: session.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
        if (session.current) {
            Text(
                stringResource(R.string.account_this_device),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
        } else {
            Text(
                stringResource(R.string.account_revoke),
                style = MaterialTheme.typography.labelLarge,
                color = colors.danger,
                modifier = Modifier.clickable(onClick = onRevoke),
            )
        }
    }
}
