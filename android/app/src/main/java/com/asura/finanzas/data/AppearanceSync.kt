package com.asura.finanzas.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val prefsJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** The account settings both apps read and write. */
private const val SETTING_KEY = "appearance"
private const val THEME_KEY = "theme"
private const val PREFS_KEY = "preferences"

/**
 * The `preferences` account setting — the same envelope `src/lib/preferences.ts`
 * writes. A field is present only if some device explicitly chose it.
 */
@Serializable
data class PrefsEnvelope(
    val updatedAt: Long = 0,
    val locale: String? = null,
    /** "12" | "24", the web's `Clock`. */
    val clock: String? = null,
    val timezone: String? = null,
    /** "light" | "dark" | "system". */
    val theme: String? = null,
    val changelogEnabled: Boolean? = null,
) {
    fun fields() = listOf(locale, clock, timezone, theme, changelogEnabled)
}

/**
 * Cross-device sync for what the account carries: the appearance
 * (last-write-wins by timestamp, the rule `src/lib/appearance.ts` applies), the
 * light/dark theme, and the preferences — language, clock, timezone, theme and
 * the "What's new" toggle — so a reinstall or a new phone restores them.
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
        preferenceChanged()
    }

    // ---- preferences ----

    /** Whose preferences these are; set by [pullPreferences] on sign-in. */
    private var userId: Long? = null
    private val prefsLock = Mutex()

    /**
     * On sign-in and on launch: adopt the account's preferences when they are
     * the newer copy, then hand the account whatever this device chose that it
     * lacks. `hydratePreferencesFromServer` on the web, same rules.
     */
    suspend fun pullPreferences(user: Long) = prefsLock.withLock {
        userId = user
        // A failed read means "unknown", not "empty": touch nothing.
        val raw = runCatching { repository.fetchSetting(PREFS_KEY) }.getOrElse { return@withLock }
        val remote = raw?.let { runCatching { prefsJson.decodeFromString(PrefsEnvelope.serializer(), it) }.getOrNull() }
        val mine = preferences.preferencesUpdatedAt(user)
        if (remote != null && remote.updatedAt >= mine) {
            applyPreferences(remote)
            preferences.setPreferencesUpdatedAt(user, remote.updatedAt)
        }
        val local = explicitPreferences()
        val missing = remote == null || local.fields().zip(remote.fields()).any { (l, r) -> l != null && r == null }
        if (remote == null || mine > remote.updatedAt || missing) push(user, local)
    }

    /** Language, clock, timezone and "What's new": saved here, sent to the account. */
    suspend fun saveLocale(locale: String) {
        preferences.setLocale(locale); preferenceChanged()
    }

    suspend fun saveClock24(value: Boolean) {
        preferences.setClock24(value); preferenceChanged()
    }

    suspend fun saveTimezone(zone: String) {
        preferences.setTimezone(zone); preferenceChanged()
    }

    suspend fun saveChangelogEnabled(on: Boolean) {
        preferences.setChangelogEnabled(on); preferenceChanged()
    }

    /** Best-effort: offline, the newer local stamp makes the next pull push it. */
    private suspend fun preferenceChanged() = prefsLock.withLock {
        val user = userId ?: return@withLock
        preferences.setPreferencesUpdatedAt(user, System.currentTimeMillis())
        push(user, explicitPreferences())
    }

    private suspend fun push(user: Long, local: PrefsEnvelope) {
        if (local.fields().all { it == null }) return
        val stamp = System.currentTimeMillis()
        preferences.setPreferencesUpdatedAt(user, stamp)
        runCatching {
            repository.setSetting(
                PREFS_KEY,
                prefsJson.encodeToString(PrefsEnvelope.serializer(), local.copy(updatedAt = stamp)),
            )
        }
    }

    private suspend fun explicitPreferences() = PrefsEnvelope(
        locale = preferences.storedLocale(),
        clock = preferences.storedClock24()?.let { if (it) "24" else "12" },
        timezone = preferences.storedTimezone(),
        theme = preferences.storedTheme()?.name?.lowercase(),
        changelogEnabled = preferences.storedChangelogEnabled(),
    )

    private suspend fun applyPreferences(remote: PrefsEnvelope) {
        remote.locale?.takeIf { it == "es" || it == "en" }?.let { preferences.setLocale(it) }
        when (remote.clock) {
            "24" -> preferences.setClock24(true)
            "12" -> preferences.setClock24(false)
        }
        remote.timezone
            ?.takeIf { runCatching { java.time.ZoneId.of(it) }.isSuccess }
            ?.let { preferences.setTimezone(it) }
        remote.theme?.let(::themeFromWire)?.let { preferences.setTheme(it) }
        remote.changelogEnabled?.let { preferences.setChangelogEnabled(it) }
    }
}

/** The web writes "light" | "dark" | "system"; the enum is capitalised. */
private fun themeFromWire(value: String): ThemeChoice? = when (value) {
    "light" -> ThemeChoice.Light
    "dark" -> ThemeChoice.Dark
    "system" -> ThemeChoice.System
    else -> null
}
