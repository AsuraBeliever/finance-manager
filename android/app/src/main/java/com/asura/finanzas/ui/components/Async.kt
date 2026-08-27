package com.asura.finanzas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asura.finanzas.data.Synced
import com.asura.finanzas.ui.theme.Broke

/** What a screen is currently showing. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T, val fromCache: Boolean) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/**
 * Runs [fetch] once per [key] and exposes the result. Reload happens by bumping
 * the key, which keeps the screens free of view models for what is, so far,
 * a single read each.
 */
@Composable
fun <T> loadSynced(key: Any, fetch: suspend () -> Synced<T>): State<Load<T>> =
    produceState<Load<T>>(initialValue = Load.Loading, key1 = key) {
        value = Load.Loading
        value = runCatching { fetch() }
            .fold(
                onSuccess = { Load.Ready(it.value, it.fromCache) },
                onFailure = { Load.Failed(it.message ?: "Algo salió mal") },
            )
    }

/** Remembered counter used as the reload key. */
@Composable
fun rememberReloadKey(): Pair<Int, () -> Unit> {
    val state = remember { mutableStateOf(0) }
    return state.value to { state.value += 1 }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Broke.colors.accent, strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
        )
        TextButton(onClick = onRetry) {
            Text("Reintentar", color = Broke.colors.accentBright)
        }
    }
}
