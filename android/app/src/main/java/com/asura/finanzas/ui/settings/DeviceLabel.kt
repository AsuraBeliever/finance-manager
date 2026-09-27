package com.asura.finanzas.ui.settings

import com.asura.finanzas.ui.LocalAppSettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.R
import com.asura.finanzas.ui.text
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The devices list, read the same way the web reads it (`src/lib/device.ts`).
 *
 * Both clients see the same rows off the same endpoint, so the phone printing
 * the raw `Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 …` where the
 * browser printed "Linux · Chrome" made the same session look like two.
 */
@Composable
fun deviceLabel(userAgent: String?): String {
    val unknown = stringResource(R.string.account_unknown_device)
    val desktopApp = stringResource(R.string.account_desktop_app)
    val androidApp = stringResource(R.string.account_android_app)
    if (userAgent.isNullOrBlank()) return unknown

    val os = when {
        userAgent.contains("iPhone") -> "iPhone"
        userAgent.contains("iPad") -> "iPad"
        userAgent.contains("Android") -> "Android"
        userAgent.contains("Windows") -> "Windows"
        Regex("Macintosh|Mac OS X").containsMatchIn(userAgent) -> "Mac"
        userAgent.contains("Linux") -> "Linux"
        else -> null
    }
    // Order matters: Chrome's UA contains "Safari", Edge's contains "Chrome".
    val browser = when {
        userAgent.contains("Edg/") -> "Edge"
        userAgent.contains("OPR/") -> "Opera"
        userAgent.contains("Chrome/") -> "Chrome"
        userAgent.contains("Firefox/") -> "Firefox"
        userAgent.contains("Safari/") -> "Safari"
        else -> null
    }
    // WebKitGTK (the Tauri desktop shell) reports Linux + Safari.
    if (os == "Linux" && browser == "Safari") return "Linux · $desktopApp"
    // This app is not a browser; it signs its own UA (see `Rpc`).
    if (userAgent.contains("Finanzas/")) return "Android · $androidApp"
    if (os != null && browser != null) return "$os · $browser"
    return os ?: browser ?: unknown
}

/** Whether the row deserves a phone glyph instead of a monitor. */
fun isMobileDevice(userAgent: String?): Boolean =
    userAgent != null && Regex("iPhone|iPad|Android|Mobile").containsMatchIn(userAgent)

/**
 * A SQLite UTC stamp as "hace 27 minutos" / "27 minutes ago" — the web writes
 * the same phrase with date-fns' `formatDistanceToNow`, not a raw timestamp,
 * and these are the wordings date-fns produces for the ranges a session can
 * live in (they expire well before date-fns would start saying "months").
 */
@Composable
fun relativeFromUtc(utc: String?): String {
    if (utc.isNullOrBlank()) return ""
    val instant = runCatching {
        LocalDateTime.parse(utc.replace(" ", "T")).atZone(ZoneId.of("UTC")).toInstant()
    }.getOrNull() ?: return utc
    val seconds = Duration.between(instant, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return formatDistanceToNow(seconds, LocalAppSettings.current.locale.startsWith("en"))
}

/**
 * date-fns' `formatDistanceToNow(date, { addSuffix: true })`, which the web
 * prints these with — thresholds and wording from its `es` / `en-US` locales
 * ("hace alrededor de 3 horas", "about 3 hours ago"), not a coarser version.
 */
private fun formatDistanceToNow(seconds: Long, english: Boolean): String {
    val minutes = Math.round(seconds / 60.0).toInt()
    fun pick(one: String, other: String, n: Int) = if (n == 1) one else other.replace("{n}", n.toString())
    val body = if (english) {
        when {
            minutes < 2 -> if (minutes == 0) "less than a minute" else "1 minute"
            minutes < 45 -> "$minutes minutes"
            minutes < 90 -> "about 1 hour"
            minutes < 1440 -> pick("about 1 hour", "about {n} hours", Math.round(minutes / 60.0).toInt())
            minutes < 2520 -> "1 day"
            minutes < 43200 -> pick("1 day", "{n} days", Math.round(minutes / 1440.0).toInt())
            minutes < 86400 -> pick("about 1 month", "about {n} months", Math.round(minutes / 43200.0).toInt())
            else -> yearsPhrase(minutes, english = true)
        }
    } else {
        when {
            minutes < 2 -> if (minutes == 0) "menos de un minuto" else "1 minuto"
            minutes < 45 -> "$minutes minutos"
            minutes < 90 -> "alrededor de 1 hora"
            minutes < 1440 -> pick("alrededor de 1 hora", "alrededor de {n} horas", Math.round(minutes / 60.0).toInt())
            minutes < 2520 -> "1 día"
            minutes < 43200 -> pick("1 día", "{n} días", Math.round(minutes / 1440.0).toInt())
            minutes < 86400 -> pick("alrededor de 1 mes", "alrededor de {n} meses", Math.round(minutes / 43200.0).toInt())
            else -> yearsPhrase(minutes, english = false)
        }
    }
    return if (english) "$body ago" else "hace $body"
}

private fun yearsPhrase(minutes: Int, english: Boolean): String {
    val months = minutes / 43200
    if (months < 12) {
        val n = Math.round(minutes / 43200.0).toInt()
        return if (english) (if (n == 1) "1 month" else "$n months") else (if (n == 1) "1 mes" else "$n meses")
    }
    val years = months / 12
    val rest = months % 12
    return when {
        rest < 3 -> if (english) (if (years == 1) "about 1 year" else "about $years years")
        else (if (years == 1) "alrededor de 1 año" else "alrededor de $years años")
        rest < 9 -> if (english) (if (years == 1) "over 1 year" else "over $years years")
        else (if (years == 1) "más de 1 año" else "más de $years años")
        else -> if (english) "almost ${years + 1} years" else "casi ${years + 1} años"
    }
}
