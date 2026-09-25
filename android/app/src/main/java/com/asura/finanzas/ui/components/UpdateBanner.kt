package com.asura.finanzas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.R
import com.asura.finanzas.data.ApkUpdate
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compare two dotted release versions. Missing or non-numeric parts count as 0,
 * so a malformed value can never claim to be newer.
 */
fun isNewerVersion(deployed: String, installed: String): Boolean {
    fun parts(v: String) = v.trim().split(".").map { it.toIntOrNull() ?: 0 }
    val a = parts(deployed)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}

/** The web re-checks hourly and whenever the window comes back into view. */
private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

/**
 * The web's `UpdateBanner` at phone width: a full-width bar resting on the tab
 * bar (`bottomOnMobile`), "Hay una nueva versión" beside an "Actualizar"
 * button, and while it works a blocking card that says so.
 *
 * What the button does is the one honest difference. The web swaps its
 * service worker and reloads; here it downloads the release APK from GitHub
 * and opens the system installer on it ([ApkUpdate]). If the asset is not
 * there yet — CI attaches it a few minutes after the deploy — the bar says so
 * and the button stays to retry.
 *
 * Lay it over the bottom of the content, above the tab bar, the way the web's
 * `fixed bottom-[var(--bottom-nav-h)]` does.
 */
@Composable
fun UpdateBanner(
    repository: BrokeRepository,
    modifier: Modifier = Modifier,
    /** Signed out there is no tab bar: the web tops the column, no rule. */
    atTop: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var deployed by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                repository.deployedAppVersion()?.let { deployed = it }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    val newer = deployed?.takeIf { isNewerVersion(it, BuildConfig.VERSION_NAME) } ?: return
    val colors = Broke.colors

    fun apply() {
        if (applying) return
        applying = true
        failed = false
        scope.launch {
            runCatching { ApkUpdate.download(context, newer) {} }
                .onSuccess { ApkUpdate.install(context, it) }
                .onFailure { failed = true }
            applying = false
        }
    }

    // `bg-accent-dim/15 px-4 py-2.5 text-xs text-accent`, `border-t`, `gap-3`.
    Column(modifier.fillMaxWidth()) {
        if (!atTop) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderMuted))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentDim.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (failed) R.string.update_download_failed else R.string.update_available),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            // `rounded-md bg-accent-dim px-3 py-1.5 font-medium text-surface`.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accentDim.copy(alpha = if (applying) 0.6f else 1f))
                    .clickable(enabled = !applying) { apply() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpinningRefresh(spinning = applying, size = 13, tint = colors.surface)
                Text(
                    stringResource(if (applying) R.string.update_updating else R.string.update_action),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.surface,
                )
            }
        }
    }

    if (applying) {
        // `fixed inset-0 bg-surface/80` over everything, with the card centred.
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                Modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        // The page gutter: the APK's hint is longer than the
                        // web's and would otherwise run the card edge to edge.
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(16.dp))
                        .padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SpinningRefresh(spinning = true, size = 28, tint = colors.accent)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.update_updating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.fg,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.update_downloading_hint),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        ),
                        color = colors.fgSubtle,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Lucide's `RefreshCw`, turning like Tailwind's `animate-spin` (1 s, linear). */
@Composable
private fun SpinningRefresh(spinning: Boolean, size: Int, tint: androidx.compose.ui.graphics.Color) {
    val angle = if (spinning) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        ).value
    } else {
        0f
    }
    Icon(Lucide.RefreshCw, contentDescription = null, tint = tint, modifier = Modifier.size(size.dp).rotate(angle))
}
