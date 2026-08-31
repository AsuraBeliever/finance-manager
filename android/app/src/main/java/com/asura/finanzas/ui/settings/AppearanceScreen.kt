package com.asura.finanzas.ui.settings

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

/**
 * Colours, type and branding — the web's `AppearancePage`. Every change saves
 * immediately and mirrors to the account, so the laptop picks it up too.
 */
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

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.appearance_title), onBack)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.appearance_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgSubtle,
            )
        }

        // A live sample so a choice can be judged before leaving the screen.
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                MicroLabel(stringResource(R.string.appearance_preview))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(current, size = 28.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        current.appName.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.fg,
                    )
                }
                Spacer(Modifier.height(10.dp))
                HeroAmount("$ 12,345.67")
            }
        }

        item { MicroLabel(stringResource(R.string.appearance_colors)) }
        item {
            SwatchRow(
                label = stringResource(R.string.appearance_accent),
                selected = current.accent,
                onSelect = { picked -> update { it.copy(accent = picked) } },
            )
        }
        item {
            SwatchRow(
                label = stringResource(R.string.appearance_secondary),
                selected = current.gold,
                onSelect = { picked -> update { it.copy(gold = picked) } },
            )
        }
        item {
            SwatchRow(
                label = stringResource(R.string.appearance_background),
                selected = current.surface,
                onSelect = { picked -> update { it.copy(surface = picked) } },
                hint = stringResource(R.string.appearance_background_hint),
            )
        }

        item { MicroLabel(stringResource(R.string.appearance_font)) }
        item {
            PickerField(
                label = stringResource(R.string.appearance_font),
                options = APPEARANCE_FONTS,
                selected = current.font,
                optionLabel = { fontLabel(it) },
                onSelect = { picked -> update { it.copy(font = picked) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { MicroLabel(stringResource(R.string.appearance_brand)) }
        item {
            FormField(
                label = stringResource(R.string.appearance_app_name),
                value = current.appName,
                onValueChange = { name -> update { it.copy(appName = name) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            IconRow(
                selected = current.icon,
                onSelect = { picked -> update { it.copy(icon = picked) } },
            )
        }

        item {
            LogoPicker(
                logo = current.logo,
                onPick = { dataUrl -> update { it.copy(logo = dataUrl) } },
                onClear = { update { it.copy(logo = "") } },
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = stringResource(R.string.appearance_reset),
                onClick = { scope.launch { sync.save(Appearance()) } },
            )
        }
    }
}

/** Swatches plus a "no override" chip that restores the theme's own colour. */
@Composable
private fun SwatchRow(
    label: String,
    selected: String?,
    onSelect: (String?) -> Unit,
    hint: String? = null,
) {
    val colors = Broke.colors
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.fgMuted)
        hint?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Empty circle = keep the built-in colour for the active theme.
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceOverlay)
                    .border(
                        if (selected == null) 2.dp else 1.dp,
                        if (selected == null) colors.accent else colors.borderMuted,
                        CircleShape,
                    )
                    .clickable { onSelect(null) },
            )
            CATEGORY_PALETTE.forEach { hex ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex) ?: colors.accent)
                        .border(
                            if (selected == hex) 2.dp else 0.dp,
                            if (selected == hex) colors.fg else colors.borderMuted,
                            CircleShape,
                        )
                        .clickable { onSelect(hex) },
                )
            }
        }
    }
}

@Composable
private fun IconRow(selected: String, onSelect: (String) -> Unit) {
    val colors = Broke.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.appearance_icon),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            APPEARANCE_ICONS.forEach { key ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceOverlay)
                        .border(
                            if (selected == key) 2.dp else 0.dp,
                            colors.accent,
                            CircleShape,
                        )
                        .clickable { onSelect(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        appearanceIcon(key),
                        contentDescription = key,
                        tint = if (selected == key) colors.accent else colors.fgMuted,
                        modifier = Modifier.size(20.dp),
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
