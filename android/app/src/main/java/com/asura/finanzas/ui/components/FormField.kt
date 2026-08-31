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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
