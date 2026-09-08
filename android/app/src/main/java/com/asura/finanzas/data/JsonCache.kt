package com.asura.finanzas.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.cacheDataStore by preferencesDataStore("broke_cache")

/**
 * Last-synced copy of each read-only RPC response, so the app opens with real
 * numbers instead of a spinner when there is no signal — the same deal the web
 * app makes with its persisted query cache.
 *
 * The server stays the single source of truth: cached values are always shown
 * behind an "sin conexión" marker, never treated as current.
 */
class JsonCache(private val context: Context) {

    suspend fun read(key: String): String? =
        context.cacheDataStore.data.first()[stringPreferencesKey(key)]

    suspend fun write(key: String, value: String) {
        context.cacheDataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    /**
     * Drop the cached reads after a write. Balances, totals and lists all move
     * when a movement is captured, so keeping any of them would show stale money
     * the next time the app opens offline.
     */
    suspend fun invalidateReads() {
        context.cacheDataStore.edit { it.clear() }
    }

    /** Wipe on logout: the next user must never see the previous one's numbers. */
    suspend fun clear() {
        context.cacheDataStore.edit { it.clear() }
    }
}
