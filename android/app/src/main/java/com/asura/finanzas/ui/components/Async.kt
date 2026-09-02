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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asura.finanzas.data.QueryCache
import com.asura.finanzas.data.Synced
import com.asura.finanzas.ui.theme.Broke

/**
 * The session's query cache, so [loadSynced] can reach it without every screen
 * threading it through. Provided from the repository in [ProvideQueryCache];
 * the default is a private one, which just means no sharing between screens.
 */
val LocalQueryCache: ProvidableCompositionLocal<QueryCache> =
    staticCompositionLocalOf { QueryCache() }

@Composable
fun ProvideQueryCache(cache: QueryCache, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQueryCache provides cache, content = content)
}

/** What a screen is currently showing. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T, val fromCache: Boolean) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/**
 * Reads [queryKey] and exposes the result, the way the web's `useQuery` does.
 *
 * Whatever the same key returned last is painted **immediately**, and [fetch]
 * runs behind it to replace it — the web's `staleTime: 0`. That is what makes
 * moving between tabs instant: the screen is thrown away when you leave it,
 * but its data is not, so coming back never shows a spinner for something
 * already read. Only a key nobody has read yet starts on [Load.Loading].
 *
 * A refetch that fails leaves what is on screen alone: the page you were
 * reading should not blank out because one background request timed out. The
 * error surfaces only when there is nothing to show instead.
 *
 * @param queryKey identifies the data — same key, same cache entry. Include
 *   everything the answer depends on (the period, the filters), and nothing
 *   else; two screens must not share a key.
 * @param refetch bump this to read again **without** dropping what is on
 *   screen, which is how a reload after a capture behaves on the web.
 */
@Composable
fun <T> loadSynced(
    queryKey: Any,
    refetch: Any = Unit,
    fetch: suspend () -> Synced<T>,
): State<Load<T>> {
    val cache = LocalQueryCache.current
    val id = queryKey.toString()

    val state = remember(id) {
        mutableStateOf<Load<T>>(
            cache.peek<T>(id)?.let { Load.Ready(it.value, it.fromCache) } ?: Load.Loading,
        )
    }

    LaunchedEffect(id, refetch) {
        runCatching { fetch() }.fold(
            onSuccess = {
                cache.put(id, it)
                state.value = Load.Ready(it.value, it.fromCache)
            },
            onFailure = {
                if (state.value !is Load.Ready) {
                    state.value = Load.Failed(it.message ?: "Algo salió mal")
                }
            },
        )
    }

    return state
}

/**
 * [loadSynced] for the secondary reads that have no offline copy — the wallet
 * categories behind a picker, the totals under a filter, who is signed in.
 *
 * These are the ones a screen can draw without, so a failure just leaves
 * [fallback] in place instead of taking the page down. They still come out of
 * the cache first, which is what stops a tab from assembling itself piece by
 * piece every time you open it.
 */
@Composable
fun <T> loadCached(
    queryKey: Any,
    refetch: Any = Unit,
    fallback: T,
    fetch: suspend () -> T,
): T {
    val state by loadSynced(queryKey, refetch) { Synced(fetch(), fromCache = false) }
    return (state as? Load.Ready)?.data ?: fallback
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
