package com.asura.finanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.R
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

/**
 * A form input drawn the way the web draws one: the label is a small muted line
 * ABOVE the control, and the control itself is a plain bordered box.
 *
 * Material's `OutlinedTextField` — with its label floating up into a notch in
 * the outline — is instantly recognisable as the Android version of a screen,
 * which is exactly what this app is not allowed to look like. Metrics come from
 * `Field` + `inputClass` in `src/components/Field.tsx`: rounded-lg, px-3 py-2,
 * text-sm, hairline border that turns accent on focus.
 *
 * Pass a blank `label` where the web shows the control on its own.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    readOnly: Boolean = false,
    /** Turns the outline red, the web's invalid state. */
    isError: Boolean = false,
    /** Trailing unit shown in muted text ("%", "MXN", "months"). */
    suffix: String = "",
    /** Drawn inside the box before the text (the amount field's currency sign). */
    leading: @Composable (() -> Unit)? = null,
    /** Drawn inside the box after the text. */
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = Broke.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier) {
        if (label.isNotBlank()) {
            FieldLabel(label)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = if (enabled) colors.fg else colors.fgSubtle,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(
                            1.dp,
                            when {
                                isError -> colors.danger
                                focused -> colors.accent
                                else -> colors.borderMuted
                            },
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    leading?.invoke()
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotBlank()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.fgSubtle,
                            )
                        }
                        inner()
                    }
                    if (suffix.isNotBlank()) {
                        Text(
                            suffix,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.fgSubtle,
                        )
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

/** The small muted caption the web puts above every control. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.8.sp),
        color = Broke.colors.fgMuted,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * Money entry, matching the web's `MoneyInput`: a `$` adornment, a "0.00"
 * placeholder, and cash-register behaviour — the digits you type fill in from
 * the right, so "1234" reads as 12.34 and the box always holds a fully grouped
 * amount.
 *
 * The value handed back is a plain major-unit string ("1234.56", or "" when
 * empty), which is what `parseAmountToCents` already expects. No money is
 * computed here; this only groups the digits someone typed.
 */
@Composable
fun MoneyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    val cents = value.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
    val display = cents?.let { groupCents(it) }.orEmpty()

    FormField(
        label = label,
        value = display,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(13)
            onValueChange(
                if (digits.isEmpty()) "" else {
                    java.math.BigDecimal(digits).movePointLeft(2).setScale(2).toPlainString()
                },
            )
        },
        modifier = modifier,
        placeholder = "0.00",
        singleLine = true,
        suffix = suffix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        leading = {
            Text(
                "$",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Broke.colors.fgSubtle,
            )
        },
    )
}

/** 1234 cents -> "12.34", grouped in thousands. */
private fun groupCents(cents: Long): String {
    val s = kotlin.math.abs(cents).toString().padStart(3, '0')
    val frac = s.takeLast(2)
    val int = s.dropLast(2).reversed().chunked(3).joinToString(",").reversed()
    return (if (cents < 0) "-" else "") + int + "." + frac
}

private fun String.toBigDecimalOrNull(): java.math.BigDecimal? =
    runCatching { java.math.BigDecimal(this) }.getOrNull()

/**
 * The shared colour picker: a row of preset swatches, exactly the palette the
 * web offers, plus a custom swatch that opens a hex entry. Used wherever a
 * colour is chosen (goals, categories, subscriptions).
 */
@Composable
fun ColorPicker(value: String?, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Broke.colors
    var custom by remember { mutableStateOf(false) }
    val isPreset = value != null && value in CATEGORY_PALETTE

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CATEGORY_PALETTE.forEach { hex ->
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex) ?: colors.accent)
                    .then(
                        if (value == hex) {
                            Modifier.border(2.dp, colors.accent, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onChange(hex) },
            )
        }
        // Anything outside the palette is a custom colour; the swatch shows it.
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (value != null && !isPreset) {
                        SolidColor(parseHexColor(value) ?: colors.accent)
                    } else {
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFEF4444), Color(0xFFEAB308), Color(0xFF22C55E),
                                Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFFA855F7),
                                Color(0xFFEF4444),
                            ),
                        )
                    },
                )
                .then(
                    if (value != null && !isPreset) {
                        Modifier.border(2.dp, colors.accent, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable { custom = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Palette,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }

    if (custom) {
        var hex by remember { mutableStateOf(value ?: "#A855F7") }
        AlertDialog(
            onDismissRequest = { custom = false },
            containerColor = colors.surfaceOverlay,
            title = { Text(stringResource(R.string.categories_custom_color), color = colors.fg) },
            text = {
                FormField(
                    label = "",
                    value = hex,
                    onValueChange = { hex = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "#A855F7",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    parseHexColor(hex)?.let { onChange(hex) }
                    custom = false
                }) { Text(stringResource(R.string.common_save), color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { custom = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.fgMuted)
                }
            },
        )
    }
}
