package com.asura.finanzas.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.Locale

private val Context.prefsDataStore by preferencesDataStore("broke_prefs")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

enum class ThemeChoice { System, Light, Dark }

data class AppSettings(
    /** "es" or "en" — a setting inside the app, exactly like on the web. */
    val locale: String,
    val theme: ThemeChoice,
    /** Hide balances on screen (the web's PrivacyToggle). */
    val hideBalances: Boolean,
    /** 24-hour clock; false shows 12-hour with am/pm. */
    val clock24: Boolean,
    /** IANA zone movements are shown in. */
    val timezone: String,
    /** Colours, type and branding — synced with the web through the account. */
    val appearance: Appearance = Appearance(),
)

/** Device-local preferences. Account-level settings live in the backend. */
class AppPreferences(private val context: Context) {

    private val localeKey = stringPreferencesKey("locale")
    private val themeKey = stringPreferencesKey("theme")
    private val hideBalancesKey = booleanPreferencesKey("hide_balances")
    private val clock24Key = booleanPreferencesKey("clock_24")
    private val timezoneKey = stringPreferencesKey("timezone")
    private val appearanceKey = stringPreferencesKey("appearance")
    private val appearanceStampKey = androidx.datastore.preferences.core.longPreferencesKey(
        "appearance_updated_at",
    )

    val settings: Flow<AppSettings> = context.prefsDataStore.data.map { prefs ->
        AppSettings(
            locale = prefs[localeKey] ?: defaultLocale(),
            theme = prefs[themeKey]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.System,
            hideBalances = prefs[hideBalancesKey] ?: false,
            clock24 = prefs[clock24Key] ?: true,
            timezone = prefs[timezoneKey] ?: java.util.TimeZone.getDefault().id,
            appearance = prefs[appearanceKey]
                ?.let { runCatching { json.decodeFromString(Appearance.serializer(), it) }.getOrNull() }
                ?.normalized()
                ?: Appearance(),
        )
    }

    /** When this device last changed the appearance, for last-write-wins sync. */
    val appearanceUpdatedAt: Flow<Long> =
        context.prefsDataStore.data.map { it[appearanceStampKey] ?: 0L }

    suspend fun setLocale(locale: String) = edit { it[localeKey] = locale }
    suspend fun setTheme(theme: ThemeChoice) = edit { it[themeKey] = theme.name }
    suspend fun setHideBalances(hide: Boolean) = edit { it[hideBalancesKey] = hide }
    suspend fun setClock24(value: Boolean) = edit { it[clock24Key] = value }
    suspend fun setTimezone(zone: String) = edit { it[timezoneKey] = zone }

    /**
     * Store the appearance and stamp it. [stamp] is the edit's own time; when
     * adopting the account's copy, pass the stamp that came with it so this
     * device does not claim authorship of someone else's change.
     */
    suspend fun setAppearance(appearance: Appearance, stamp: Long = System.currentTimeMillis()) =
        edit {
            it[appearanceKey] = json.encodeToString(Appearance.serializer(), appearance)
            it[appearanceStampKey] = stamp
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.prefsDataStore.edit(block)
    }

    // Same rule as the web's i18n store: anything non-English falls back to
    // Spanish, the app's original locale and primary audience.
    private fun defaultLocale(): String =
        if (Locale.getDefault().language.lowercase().startsWith("en")) "en" else "es"
}
