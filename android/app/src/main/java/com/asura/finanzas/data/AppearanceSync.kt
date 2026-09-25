package com.asura.finanzas.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** The account settings both apps read and write. */
private const val SETTING_KEY = "appearance"
private const val THEME_KEY = "theme"

/**
 * Cross-device sync for the two look settings the account carries: the
 * appearance (last-write-wins by timestamp, the rule `src/lib/appearance.ts`
 * applies) and the light/dark theme.
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

    /**
     * Adopt the account's theme, but only on a device that has never picked
     * one — `hydrateThemeFromServer` in `src/lib/theme.ts`, same rule. An
     * explicit local choice always wins, or the app would flip on every
     * launch.
     *
     * Without this pair the theme was the one account setting the phone did
     * not share: switching to light on the web left the APK dark for good.
     */
    suspend fun pullTheme() {
        if (preferences.storedTheme() != null) return
        val remote = runCatching { repository.getSetting(THEME_KEY) }.getOrNull() ?: return
        themeFromWire(remote)?.let { preferences.setTheme(it) }
    }

    /** Save locally and mirror to the account, as `setThemePref` does. */
    suspend fun saveTheme(theme: ThemeChoice) {
        preferences.setTheme(theme)
        runCatching { repository.setSetting(THEME_KEY, theme.name.lowercase()) }
    }
}

/** The web writes "light" | "dark" | "system"; the enum is capitalised. */
private fun themeFromWire(value: String): ThemeChoice? = when (value) {
    "light" -> ThemeChoice.Light
    "dark" -> ThemeChoice.Dark
    "system" -> ThemeChoice.System
    else -> null
}
