package com.asura.finanzas.data

import java.util.concurrent.ConcurrentHashMap

/**
 * The last answer for each read, kept in memory for as long as the session
 * lasts — the in-process half of what TanStack Query gives the web app.
 *
 * The point is what happens when you come back to a screen. The web paints the
 * cached numbers immediately and refetches behind them, so moving between pages
 * feels instant; without this the phone threw away every screen's data the
 * moment you left its tab and met you with a spinner on the way back.
 *
 * It is a cache of what the server last said, not a source of truth: every
 * reader still refetches, and the fresh answer replaces this one. It is also
 * **not** the offline snapshot — that one is [JsonCache], survives restarts,
 * and is shown behind the "sin conexión" banner. This one dies with the
 * process and is never labelled, precisely because it is only ever a few
 * hundred milliseconds out of date.
 *
 * Writes deliberately do **not** clear it. A capture invalidates the offline
 * snapshot and every screen refetches, but the stale figure stays up for the
 * instant that takes rather than collapsing back into a spinner — which is the
 * same trade the web makes with `staleTime: 0`.
 */
class QueryCache {

    private val entries = ConcurrentHashMap<String, Synced<*>>()

    /**
     * The cached answer for [key], or null when nothing has been read yet.
     *
     * The cast is safe by construction: a key names one call site, and a call
     * site only ever stores the one type it fetches.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> peek(key: String): Synced<T>? = entries[key] as Synced<T>?

    fun put(key: String, value: Synced<*>) {
        entries[key] = value
    }

    /** Wipe on logout: the next user must never see the previous one's numbers. */
    fun clear() {
        entries.clear()
    }
}
