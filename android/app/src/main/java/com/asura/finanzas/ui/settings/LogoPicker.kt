package com.asura.finanzas.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.Appearance
import com.asura.finanzas.ui.theme.Broke
import java.io.ByteArrayOutputStream

/** Same cap the web applies before storing the data URL. */
private const val MAX_LOGO_PX = 128

/**
 * The brand mark: the uploaded logo when there is one, otherwise the chosen
 * icon — the same precedence the web uses.
 */
@Composable
fun BrandMark(appearance: Appearance, size: Dp) {
    val bitmap = decodeLogo(appearance.logo)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Icon(
            appearanceIcon(appearance.icon),
            contentDescription = null,
            tint = Broke.colors.accent,
            modifier = Modifier.size(size),
        )
    }
}

/** Decode a `data:image/...;base64,` URL, or null when absent or malformed. */
fun decodeLogo(dataUrl: String): Bitmap? {
    if (dataUrl.isBlank()) return null
    val comma = dataUrl.indexOf(',').takeIf { it >= 0 } ?: return null
    return runCatching {
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * Pick an image from the device and store it as a data URL, downscaled to
 * [MAX_LOGO_PX] on its longest side so the account setting stays small — the
 * same trade the web makes when it resizes before saving.
 */
@Composable
fun LogoPicker(logo: String, onPick: (String) -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val colors = Broke.colors

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val encoded = runCatching {
            val source = context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it)
            } ?: return@runCatching null
            val scale = MAX_LOGO_PX.toFloat() / maxOf(source.width, source.height)
            val scaled = if (scale >= 1f) {
                source
            } else {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
        encoded?.let(onPick)
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.appearance_logo),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgMuted,
        )
        Text(
            stringResource(R.string.appearance_logo_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            decodeLogo(logo)?.let {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceOverlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.appearance_upload_logo),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.clickable {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            if (logo.isNotBlank()) {
                Text(
                    stringResource(R.string.appearance_remove_logo),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.danger,
                    modifier = Modifier.clickable { onClear() },
                )
            }
        }
    }
}
