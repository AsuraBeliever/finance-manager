package com.asura.finanzas.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.asura.finanzas.R
import com.asura.finanzas.ui.theme.Broke

/**
 * The web's overlay is `bg-black/70 backdrop-blur-sm`: the dim comes from
 * `android:backgroundDimAmount` in the theme, but the blur has to be asked for
 * on the dialog's own window — a theme attribute never reaches it. Without it
 * the page underneath stays legible through the scrim and the two apps read
 * differently at a glance. No-op before Android 12 and on devices that have
 * blurs turned off (battery saver, low-end): the dim alone still holds up.
 */
@Composable
fun DialogBlurBehind(radius: Dp = 10.dp) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    LaunchedEffect(view, px) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = px }
    }
}

/**
 * The form every create/edit screen opens.
 *
 * It is a centred dialog, not a Material bottom sheet: the web renders these as
 * a card in the middle of the screen with a titled header, an X, and a
 * Cancel/Save pair in the bottom-right — a sheet sliding up from the bottom
 * with a drag handle is the single most "this is the Android one" tell there is.
 *
 * Metrics come from `src/components/Modal.tsx`: max-w-md, rounded-2xl, a
 * hairline border, header px-5 py-4 over a divider, body px-5 py-4.
 */
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
    val colors = Broke.colors

    Dialog(
        onDismissRequest = onDismiss,
        // The card sizes itself; the platform's default dialog width would cap
        // it well short of the web's.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogBlurBehind()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceOverlay)
                .border(1.dp, colors.borderMuted, RoundedCornerShape(16.dp))
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.displayLarge
                        .copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = colors.fg,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.common_close),
                    tint = colors.fgMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(4.dp)
                        .size(18.dp),
                )
            }
            HairLine()

            Column(
                modifier = Modifier
                    // The body scrolls while the header stays put, as on the web.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                fields()

                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.danger,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        GhostButton(stringResource(R.string.common_cancel), onDismiss)
                        PrimaryButton(text = saveLabel, onClick = onSave, enabled = canSave)
                    }
                }
            }
        }
    }
}

/** The web's `variant="ghost"` button: text only, no fill. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = Broke.colors.fg,
        )
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

/**
 * The same dialog shell without the Cancel/Save footer, for steps that are a
 * choice rather than a form (the investment catalogue).
 */
@Composable
fun PlainSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Broke.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogBlurBehind()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceOverlay)
                .border(1.dp, colors.borderMuted, RoundedCornerShape(16.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.displayLarge
                        .copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = colors.fg,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.common_close),
                    tint = colors.fgMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(4.dp)
                        .size(18.dp),
                )
            }
            HairLine()
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}


/** Yes/no confirmation, the web's `ConfirmDialog`. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Broke.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceOverlay,
        title = { Text(title, color = colors.fg) },
        text = {
            DialogBlurBehind()
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgMuted,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete), color = colors.danger)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.fgMuted)
            }
        },
    )
}
