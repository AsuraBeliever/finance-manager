package com.asura.finanzas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.ui.theme.Broke

/**
 * The bottom sheet every create/edit form uses: title, fields, an error line and
 * a save button that turns into a spinner. Keeps the forms themselves down to
 * their actual fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    busy: Boolean,
    error: String?,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveLabel: String = stringResource(R.string.common_save),
    fields: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = Broke.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceOverlay,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.fg)

            fields()

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }

            Spacer(Modifier.height(4.dp))
            if (busy) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            } else {
                PrimaryButton(
                    text = saveLabel,
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Row of tappable actions shown in the long-press dialogs. */
@Composable
fun DialogAction(
    label: String,
    color: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = color ?: Broke.colors.fg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
