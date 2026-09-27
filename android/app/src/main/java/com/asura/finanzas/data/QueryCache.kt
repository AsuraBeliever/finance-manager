package com.asura.finanzas.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The last answer for each read — what TanStack Query plus its persister give
 * the web app.
 *
 * The point is what happens when a screen opens. The web paints the cached
 * numbers immediately and refetches behind them, so every page feels instant;
 * without this the phone met you with a spinner on every tab.
 *
 * Two layers:
 * - **Memory**, for the session: coming back to a tab repaints at once.
 * - **Disk** ([file]), so the same holds after the process dies — the app
 *   being swiped away, the phone rebooting, and every update, which kills
 *   the old process. Keeping only memory meant each of those met the user
 *   with every window loading again one at a time. The web persists its
 *   query cache to localStorage for exactly this reason.
 *
 * It is a cache of what the server last said, not a source of truth: every
 * reader still refetches, and the fresh answer replaces this one. It is also
 * not the offline snapshot ([JsonCache]), which backs a read that failed and
 * is shown behind the "sin conexión" marker.
 *
 * Writes deliberately do **not** clear it. A capture invalidates the offline
 * snapshot and every screen refetches, but the stale figure stays up for the
 * instant that takes rather than collapsing back into a spinner — the same
 * trade the web makes with `staleTime: 0`.
 */
class QueryCache(private val file: File? = null) {

    private val entries = ConcurrentHashMap<String, Synced<*>>()

    /** What the disk holds, as raw JSON per key; decoded on first peek. */
    private val stored = ConcurrentHashMap<String, String>()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /**
     * Bumped by [invalidateAll]; every live reader keys on it, so a bump makes
     * each screen on display fetch again — TanStack's `invalidateQueries()`.
     * What is on screen stays up until the fresh answer lands.
     */
    val generation = androidx.compose.runtime.mutableIntStateOf(0)

    /** Refetch everything that is being shown (e.g. after the outbox drains). */
    fun invalidateAll() {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { generation.intValue++ }
    }

    init {
        // Read once, before the first frame: a few hundred KB at most, and the
        // whole point is having it when the first screen composes.
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { json.decodeFromString(diskSerializer, f.readText()) }
                .onSuccess { stored.putAll(it) }
                .onFailure { f.delete() }
        }
    }

    /**
     * The cached answer for [key], or null when nothing has been read yet.
     *
     * The cast is safe by construction: a key names one call site, and a call
     * site only ever stores the one type it fetches. [serializer] is that type's,
     * and is what lets a copy read back from disk; without one only the memory
     * layer answers.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> peek(key: String, serializer: KSerializer<T>? = null): Synced<T>? {
        entries[key]?.let { return it as Synced<T> }
        val raw = stored[key] ?: return null
        if (serializer == null) return null
        val decoded = runCatching { json.decodeFromString(serializer, raw) }
        // A shape that changed in an update just starts over from the network.
        val value = decoded.getOrElse { stored.remove(key); return null }
        return Synced(value, fromCache = false).also { entries[key] = it }
    }

    fun <T> put(key: String, value: Synced<T>, serializer: KSerializer<T>? = null) {
        entries[key] = value
        // Only live answers go to disk; an offline copy is already on disk elsewhere.
        if (file == null || serializer == null || value.fromCache) return
        val raw = runCatching { json.encodeToString(serializer, value.value) }.getOrNull() ?: return
        stored[key] = raw
        io.launch { save() }
    }

    /** Wipe on logout: the next user must never see the previous one's numbers. */
    fun clear() {
        entries.clear()
        stored.clear()
        io.launch { file?.delete() }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(diskSerializer, HashMap(stored)))
            tmp.renameTo(f)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val diskSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
