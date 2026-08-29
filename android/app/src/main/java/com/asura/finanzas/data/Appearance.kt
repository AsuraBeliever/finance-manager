package com.asura.finanzas.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User appearance, shared with the web app.
 *
 * The web keeps this in `src/lib/appearance.ts` and mirrors it to the account
 * under the `appearance` setting, wrapped in a stamped envelope so devices can
 * tell whose copy is newer. Reading and writing the same key with the same
 * shape is what makes a colour picked on the phone show up on the laptop.
 */
@Serializable
data class Appearance(
    /** Null keeps the built-in per-theme accent. */
    val accent: String? = null,
    /** Secondary "pop" colour (called gold in CSS for historical reasons). */
    val gold: String? = null,
    /** Background tint, mixed over the theme base. */
    val surface: String? = null,
    val font: String = "default",
    /** Empty shows the brand name; a value overrides it. */
    val appName: String = "",
    val icon: String = "trending-up",
    /** Data URL of an uploaded logo, or empty for the icon. */
    val logo: String = "",
)

/**
 * What the account stores: the appearance plus the stamp of its last edit.
 * Older clients wrote the bare object with no stamp, which parses as ts 0 —
 * the oldest possible, so any real edit wins over it.
 */
@Serializable
data class AppearanceEnvelope(
    val updatedAt: Long = 0,
    val value: Appearance = Appearance(),
)

/** The app name the brand used to default to; treat it as "unset". */
private const val LEGACY_APP_NAME = "Finanzas"

fun Appearance.normalized(): Appearance =
    if (appName == LEGACY_APP_NAME) copy(appName = "") else this

/** The six type pairings the web offers, by key. */
val APPEARANCE_FONTS = listOf("default", "editorial", "modern", "classic", "rounded", "system")

/** The ten brand glyphs the web offers, by key. */
val APPEARANCE_ICONS = listOf(
    "trending-up", "wallet", "piggy-bank", "coins", "landmark",
    "gem", "sparkles", "rocket", "leaf", "heart",
)

/** Tolerates both the stamped envelope and the legacy stampless object. */
fun parseAppearance(json: Json, raw: String): AppearanceEnvelope? = runCatching {
    json.decodeFromString(AppearanceEnvelope.serializer(), raw)
}.getOrNull()?.takeIf { it.updatedAt != 0L || raw.contains("\"value\"") }
    ?: runCatching {
        AppearanceEnvelope(0, json.decodeFromString(Appearance.serializer(), raw))
    }.getOrNull()
