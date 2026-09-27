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
import com.asura.finanzas.ui.components.loadCached
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.SettingRow
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import androidx.compose.foundation.border
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.PanelCard
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
    /** Categories live under the planning sheet, which this screen cannot open. */
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    var showAppearance by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var reloadSessions by remember { mutableStateOf(0) }

    val user = loadCached<User?>("me", fallback = null) { repository.me() }
    val walletCategories = loadCached(
        "walletCategories",
        fallback = emptyList<WalletCategory>(),
    ) { repository.walletCategories() }
    val sessions = loadCached(
        "sessions",
        refetch = reloadSessions,
        fallback = emptyList<SessionInfo>(),
    ) { repository.sessions() }

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



    // A modal over the settings page, as on the web — not a page of its own.
    if (showWhatsNew) {
        WhatsNewDialog(onDismiss = { showWhatsNew = false })
    }

    if (showPassword) {
        ChangePasswordScreen(
            repository = repository,
            onBack = { showPassword = false },
            modifier = modifier,
        )
        return
    }


    val themeIcon: (ThemeChoice) -> ImageVector = {
        when (it) {
            ThemeChoice.Light -> Lucide.Sun
            ThemeChoice.Dark -> Lucide.Moon
            ThemeChoice.System -> Lucide.Monitor
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

    // `[&>section]:mb-6`: 24 between cards, the header's `mb-7` above them.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp)),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PageHeader(stringResource(R.string.settings_title), Modifier.padding(bottom = 4.dp))

        // `flex flex-wrap items-center justify-between gap-3`.
        PanelCard(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.theme_label)) {
                SegmentedControl(
                    style = SegStyle.Theme,
                    // Web order: Light, Dark, Auto.
                    options = listOf(ThemeChoice.Light, ThemeChoice.Dark, ThemeChoice.System),
                    selected = settings.theme,
                    label = { themeLabel(it) },
                    icon = themeIcon,
                    onSelect = { scope.launch { appearanceSync.saveTheme(it) } },
                )
            }
        }

        PanelCard(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_language)) {
                SegmentedControl(
                    style = SegStyle.Tray,
                    options = Locale.entries,
                    selected = Locale.entries.first { it.tag == settings.locale },
                    label = { it.label },
                    onSelect = { scope.launch { appearanceSync.saveLocale(it.tag) } },
                )
            }
        }

        PanelCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_timezone),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                stringResource(R.string.settings_timezone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgSubtle,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // The web's select sits bare under the hint — no second label.
            PickerField(
                label = "",
                options = timezones,
                selected = settings.timezone,
                // The web prints zone ids with spaces, not underscores.
                optionLabel = { it.replace('_', ' ') },
                onSelect = { scope.launch { appearanceSync.saveTimezone(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            // `mt-4 flex items-center justify-between gap-3`.
            Spacer(Modifier.height(16.dp))
            SettingRow(stringResource(R.string.settings_clock), subtle = true) {
                SegmentedControl(
                    style = SegStyle.Tray,
                    options = listOf(false, true),
                    selected = settings.clock24,
                    label = {
                        stringResource(if (it) R.string.settings_clock24 else R.string.settings_clock12)
                    },
                    onSelect = { scope.launch { appearanceSync.saveClock24(it) } },
                )
            }
        }

        LinkCard(
            icon = Lucide.Palette,
            title = stringResource(R.string.appearance_title),
            hint = stringResource(R.string.appearance_settings_hint),
            link = stringResource(R.string.appearance_manage),
            onClick = { showAppearance = true },
        )

        LinkCard(
            icon = Lucide.Tags,
            title = stringResource(R.string.categories_title),
            hint = stringResource(R.string.categories_settings_hint),
            link = stringResource(R.string.categories_manage),
            onClick = onOpenCategories,
        )

        if (walletCategories.isNotEmpty()) {
            PanelCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_wallet_categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // `flex flex-wrap gap-2`, pills `rounded-full px-3 py-1 text-sm`.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    walletCategories.forEach { category ->
                        Text(
                            seedName(category.name, category.isSystem).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.fg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surfaceOverlay)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        PanelCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Lucide.Smartphone, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_devices), style = MaterialTheme.typography.titleMedium, color = colors.fg)
            }
            sessions.forEachIndexed { index, session ->
                if (index > 0) com.asura.finanzas.ui.components.HairLine()
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
                GhostButton(
                    stringResource(R.string.account_revoke_others),
                    onClick = {
                        scope.launch {
                            runCatching { repository.revokeOtherSessions() }
                            reloadSessions++
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // A link card on the web: key, title over hint, chevron. It is an
        // `<a>`, not a `<section>`, so the `mb-6` the sections get skips it and
        // it sits flush on the session card below — kept that way.
        Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
                .clickable { showPassword = true }
                .padding(21.dp),
        ) {
            Icon(Lucide.KeyRound, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.account_change_password),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Text(
                    stringResource(R.string.account_password_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgSubtle,
                )
            }
            Icon(Lucide.ChevronRight, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
        }

        PanelCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_session),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    user?.email.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                    modifier = Modifier.weight(1f, fill = false),
                )
                GhostButton(
                    stringResource(R.string.auth_logout),
                    onClick = { scope.launch { repository.logout(); onSignedOut() } },
                    leadingIcon = Lucide.LogOut,
                    iconSize = 16.dp,
                )
            }
        }

        }

        PanelCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Lucide.Sparkles, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.titleMedium, color = colors.fg)
            }
            Text(
                stringResource(R.string.whats_new_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgSubtle,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // `flex flex-wrap items-center justify-between gap-3`: the link, then
            // the checkbox with its label.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterVertically).clickable { showWhatsNew = true },
                ) {
                    Text(
                        stringResource(R.string.whats_new_view),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                    Icon(Lucide.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterVertically).clickable {
                        scope.launch { appearanceSync.saveChangelogEnabled(!settings.changelogEnabled) }
                    },
                ) {
                    WebCheckbox(
                        checked = settings.changelogEnabled,
                        onCheckedChange = { scope.launch { appearanceSync.saveChangelogEnabled(it) } },
                    )
                    Text(
                        stringResource(R.string.whats_new_notify_on_update),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                }
            }
        }

        PanelCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Lucide.Info, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, color = colors.fg)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium, color = colors.fgMuted)
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = colors.fg,
                )
            }
            // Android only (asked for on 2026-09-26): the web updates itself.
            Spacer(Modifier.height(16.dp))
            com.asura.finanzas.ui.components.CheckForUpdatesButton(repository)
        }
    }
}

/**
 * The web's appearance / categories cards: an icon heading, a hint, and an
 * accent link with a chevron that is the way in.
 */
@Composable
private fun LinkCard(
    icon: ImageVector,
    title: String,
    hint: String,
    link: String,
    onClick: () -> Unit,
) {
    val colors = Broke.colors
    PanelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.fg)
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.fgSubtle,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(link, style = MaterialTheme.typography.labelLarge, color = colors.accent)
            Icon(Lucide.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
        }
    }
}

/** One signed-in device (`flex items-center gap-3 py-2.5 text-sm`). */
@Composable
private fun SessionRow(session: SessionInfo, onRevoke: () -> Unit) {
    val colors = Broke.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (isMobileDevice(session.userAgent)) Lucide.Smartphone else Lucide.Monitor,
            contentDescription = null,
            tint = colors.fgSubtle,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    deviceLabel(session.userAgent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.current) {
                    Text(
                        stringResource(R.string.account_this_device),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                        color = colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accentDim.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.account_last_seen) + ": " +
                    relativeFromUtc(session.lastSeenAt ?: session.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgSubtle,
            )
        }
        if (!session.current) {
            // `variant="danger" className="px-3 py-1.5"`.
            Text(
                stringResource(R.string.account_revoke),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRevoke)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
