package com.asura.finanzas.ui.settings

import com.asura.finanzas.ui.components.FieldLabel
import com.asura.finanzas.ui.components.ColorPicker
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.Dot
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.R
import com.asura.finanzas.data.APPEARANCE_FONTS
import com.asura.finanzas.data.APPEARANCE_ICONS
import com.asura.finanzas.data.Appearance
import com.asura.finanzas.data.AppearanceSync
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.CATEGORY_PALETTE
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/** The palette entries the theme itself uses, shown as chosen when unset. */
private const val DEFAULT_ACCENT = "#a855f7"
private const val DEFAULT_GOLD = "#22d3ee"

/**
 * Colours, type and branding — the web's `AppearancePage`. Every change saves
 * immediately and mirrors to the account, so the laptop picks it up too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    sync: AppearanceSync,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = LocalAppSettings.current.appearance
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    fun update(block: (Appearance) -> Appearance) {
        scope.launch { sync.save(block(current)) }
    }

    // Outside the list: a LazyColumn prefetches items on a pass that has no
    // activity behind it, and `rememberLauncherForActivityResult` needs one.
    val pickLogo = rememberLogoLauncher { dataUrl -> update { it.copy(logo = dataUrl) } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(
                stringResource(R.string.appearance_title),
                onBack,
                backLabel = stringResource(R.string.settings_back),
            ) {
                // Reset is a ghost action up in the header on the web, not a
                // primary button parked at the bottom of the page.
                GhostAction(
                    icon = Lucide.RotateCcw,
                    text = stringResource(R.string.appearance_reset),
                    onClick = { scope.launch { sync.save(Appearance()) } },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.appearance_settings_hint),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgSubtle,
            )
        }

        // Colours, with the money headline as its own live sample — the web
        // puts the preview inside this card rather than above the page.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.appearance_colors))
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroAmount("$12,345.00", fontSize = 30.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Dot(colors.accent, 12.dp)
                        Text(
                            stringResource(R.string.appearance_accent),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.fgMuted,
                        )
                        Spacer(Modifier.width(6.dp))
                        Dot(colors.cyan, 12.dp)
                        Text(
                            stringResource(R.string.appearance_secondary),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.fgMuted,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                // The web shows the theme's own colour as the chosen swatch
                // when nothing has been overridden, rather than nothing at all.
                SwatchRow(
                    label = stringResource(R.string.appearance_accent),
                    selected = current.accent ?: DEFAULT_ACCENT,
                    onSelect = { picked -> update { it.copy(accent = picked) } },
                )
                Spacer(Modifier.height(16.dp))
                SwatchRow(
                    label = stringResource(R.string.appearance_secondary),
                    selected = current.gold ?: DEFAULT_GOLD,
                    onSelect = { picked -> update { it.copy(gold = picked) } },
                )
                Spacer(Modifier.height(16.dp))
                SwatchRow(
                    label = stringResource(R.string.appearance_background),
                    selected = current.surface,
                    onSelect = { picked -> update { it.copy(surface = picked) } },
                    hint = stringResource(R.string.appearance_background_hint),
                )
            }
        }

        // Typography: named chips, each set in its own face, not a dropdown.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.appearance_font))
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    APPEARANCE_FONTS.forEach { key ->
                        val chosen = current.font == key
                        Text(
                            fontLabel(key),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = if (chosen) colors.accent else colors.fg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (chosen) colors.accentDim.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                )
                                .border(
                                    1.dp,
                                    if (chosen) colors.accent else colors.borderMuted,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { update { it.copy(font = key) } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Brand and logo, with its own small preview inside the card.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.appearance_brand))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The web frames the mark in a 36 px accent-gradient tile.
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(colors.accent, colors.accentDim),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BrandMark(current, size = 18.dp, tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        current.appName.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                        color = colors.fg,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(16.dp))
                FormField(
                    label = stringResource(R.string.appearance_app_name),
                    value = current.appName,
                    onValueChange = { name -> update { it.copy(appName = name) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.app_name),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                IconRow(
                    selected = current.icon,
                    onSelect = { picked -> update { it.copy(icon = picked) } },
                )
                Spacer(Modifier.height(16.dp))
                LogoPicker(
                    logo = current.logo,
                    onPickImage = { pickLogo(Unit) },
                    onClear = { update { it.copy(logo = "") } },
                )
            }
        }
    }
}

/** A section heading inside an Appearance card — the web's `h3 font-medium`. */
@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Broke.colors.fg)
}

/** The web's ghost Button: plain text on a rounded hit area, with a glyph. */
@Composable
private fun GhostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val colors = Broke.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.fg)
    }
}

/** A labelled colour field, the web's `<Field><ColorPicker/></Field>`. */
@Composable
private fun SwatchRow(
    label: String,
    selected: String?,
    onSelect: (String?) -> Unit,
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        ColorPicker(value = selected, onChange = { onSelect(it) })
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = Broke.colors.fgSubtle,
            )
        }
    }
}

@Composable
private fun IconRow(selected: String, onSelect: (String) -> Unit) {
    val colors = Broke.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.appearance_icon),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fgMuted,
        )
        Spacer(Modifier.height(8.dp))
        // Square outlined tiles that wrap, as on the web — not a scrolling
        // strip of circles.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            APPEARANCE_ICONS.forEach { key ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected == key) colors.accentDim.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .border(
                            1.dp,
                            if (selected == key) colors.accent else colors.borderMuted,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        appearanceIcon(key),
                        contentDescription = key,
                        tint = if (selected == key) colors.accent else colors.fgMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun fontLabel(key: String): String = stringResource(
    when (key) {
        "editorial" -> R.string.appearance_fonts_editorial
        "modern" -> R.string.appearance_fonts_modern
        "classic" -> R.string.appearance_fonts_classic
        "rounded" -> R.string.appearance_fonts_rounded
        "system" -> R.string.appearance_fonts_system
        else -> R.string.appearance_fonts_default
    },
)
