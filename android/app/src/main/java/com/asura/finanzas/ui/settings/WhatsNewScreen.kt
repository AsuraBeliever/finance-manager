package com.asura.finanzas.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.GlassCard
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

    AlertDialog(
        onDismissRequest = { close() },
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.whats_new_title), color = colors.fg) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
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
                            Text("•", color = colors.accent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.fgMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { close() }) {
                Text(stringResource(R.string.common_close), color = colors.fgMuted)
            }
        },
    )
}

@Composable
fun WhatsNewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalAppSettings.current.locale
    val colors = Broke.colors

    val entries by produceState<List<ChangelogEntry>?>(initialValue = null) {
        value = readChangelog(context)
    }

    val list = entries
    if (list == null) {
        LoadingBox(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(stringResource(R.string.whats_new_title), onBack) }

        items(list, key = { it.version }) { entry ->
            GlassCard(Modifier.fillMaxWidth()) {
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
                        Text("•", color = colors.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.fgMuted,
                        )
                    }
                }
            }
        }
    }
}
