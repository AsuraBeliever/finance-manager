package com.asura.finanzas.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.R
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.ui.components.isNewerVersion
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.PlainSheet
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.theme.Broke
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mirrors the web's "What's new". The entries come from
 * `assets/changelog.json`, generated from `src/lib/changelog.ts` — the release
 * notes are written once and both apps read the same list.
 */
@Serializable
private data class ChangelogEntry(
    val version: String,
    val date: String,
    val es: List<String> = emptyList(),
    val en: List<String> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

private fun readChangelog(context: Context): List<ChangelogEntry> =
    runCatching {
        val text = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<ChangelogEntry>>(text)
    }.getOrDefault(emptyList())

/**
 * What to pop after an update, mirroring the web's `unseenEntries`: on a fresh
 * install only the running release, otherwise everything between what was last
 * seen and what is running now.
 */
private fun unseenEntries(
    all: List<ChangelogEntry>,
    current: String,
    since: String?,
): List<ChangelogEntry> {
    if (since == null) return all.filter { it.version == current }
    return all.filter {
        isNewerVersion(it.version, since) && !isNewerVersion(it.version, current)
    }
}

/**
 * The changelog, on its own, right after an update — the web shows it the same
 * way. Closing marks the running release as seen so it never repeats, and the
 * whole thing is skipped when the user turned the notice off in Ajustes.
 */
@Composable
fun WhatsNewAuto(preferences: AppPreferences) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val locale = settings.locale
    val colors = Broke.colors
    val scope = rememberCoroutineScope()
    val current = BuildConfig.VERSION_NAME

    var dismissed by remember { mutableStateOf(false) }
    val entries by produceState(
        initialValue = emptyList<ChangelogEntry>(),
        settings.changelogEnabled,
        settings.changelogSeen,
    ) {
        value = if (!settings.changelogEnabled || settings.changelogSeen == current) {
            emptyList()
        } else {
            unseenEntries(readChangelog(context), current, settings.changelogSeen)
        }
    }

    if (dismissed || entries.isEmpty()) return

    fun close() {
        dismissed = true
        scope.launch { preferences.markChangelogSeen(current) }
    }

    PlainSheet(title = stringResource(R.string.whats_new_title), onDismiss = { close() }) {
        // No scrolling column here: `PlainSheet` already scrolls its content,
        // and a second one inside it is handed an infinite height, which
        // Compose refuses — it took the app down on the update notice.
        entries.forEach { entry -> ChangelogEntryBlock(entry, locale) }
    }
}

/**
 * One release: version, date, and its lines. The web draws the same block in
 * the modal and nowhere else, so both places here share it.
 */
@Composable
private fun ChangelogEntryBlock(entry: ChangelogEntry, locale: String) {
    val colors = Broke.colors
    Row {
        Text(
            "v${entry.version}",
            style = MaterialTheme.typography.titleMedium,
            color = colors.fg,
        )
        Spacer(Modifier.width(10.dp))
        MicroLabel(entry.date, color = colors.fgSubtle)
    }
    Spacer(Modifier.height(10.dp))
    val lines = if (locale == "en") entry.en else entry.es
    lines.forEach { line ->
        Row(Modifier.padding(bottom = 8.dp)) {
            // The web marks each line with a sparkles glyph, not a bullet.
            Icon(
                Lucide.Sparkles,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.padding(top = 3.dp).size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalAppSettings.current.locale

    val entries by produceState<List<ChangelogEntry>?>(initialValue = null) {
        value = readChangelog(context)
    }

    // The web opens this as a modal over the settings page — the same one it
    // pops after an update — not as a page of its own.
    PlainSheet(title = stringResource(R.string.whats_new_title), onDismiss = onDismiss) {
        val list = entries
        if (list == null) {
            LoadingBox()
        } else {
            list.forEach { entry -> ChangelogEntryBlock(entry, locale) }
        }
    }
}
