package com.asura.finanzas.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.theme.Broke

/**
 * Compare two dotted release versions. Missing or non-numeric parts count as 0,
 * so a malformed value can never claim to be newer.
 */
internal fun isNewerVersion(deployed: String, installed: String): Boolean {
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

/**
 * The web reloads itself when a new build is deployed; a sideloaded APK cannot,
 * so this only says a newer release exists. Installing it stays manual, which is
 * why there is no action button — promising one we cannot honour would be worse
 * than saying nothing.
 */
@Composable
fun UpdateNotice(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val deployed by produceState<String?>(null) {
        value = repository.deployedAppVersion()
    }

    val newer = deployed?.takeIf { isNewerVersion(it, BuildConfig.VERSION_NAME) } ?: return
    val colors = Broke.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = colors.accent,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.update_available),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
            )
            Text(
                "${BuildConfig.VERSION_NAME} → $newer",
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
    }
}
