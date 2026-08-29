package com.asura.finanzas.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** The account setting both apps read and write. */
private const val SETTING_KEY = "appearance"

/**
 * Cross-device appearance sync, last-write-wins by timestamp — the same rule
 * the web applies in `src/lib/appearance.ts`.
 *
 * On sign-in the account copy is adopted when this device has never saved one,
 * or when the account's stamp is newer than the local one. A local edit always
 * pushes, stamped with its own time.
 */
class AppearanceSync(
    private val repository: BrokeRepository,
    private val preferences: AppPreferences,
) {

    /** Pull the account copy and adopt it when it is the newer of the two. */
    suspend fun pull() {
        val raw = repository.getSetting(SETTING_KEY) ?: return
        val remote = parseAppearance(syncJson, raw) ?: return
        val localStamp = preferences.appearanceUpdatedAt.first()
        // A device that never saved has stamp 0, so any account copy wins.
        if (remote.updatedAt >= localStamp) {
            preferences.setAppearance(remote.value.normalized(), remote.updatedAt)
        }
    }

    /** Save locally and mirror to the account so other devices pick it up. */
    suspend fun save(appearance: Appearance) {
        val stamp = System.currentTimeMillis()
        preferences.setAppearance(appearance, stamp)
        // A failed push is not worth surfacing: the local change already
        // applied, and the next edit (or another device's) will reconcile.
        runCatching {
            repository.setSetting(
                SETTING_KEY,
                syncJson.encodeToString(
                    AppearanceEnvelope.serializer(),
                    AppearanceEnvelope(stamp, appearance),
                ),
            )
        }
    }
}
