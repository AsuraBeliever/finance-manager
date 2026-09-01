package com.asura.finanzas.ui.settings

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
    val minutes = Duration.between(instant, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> text(R.string.account_moments_ago)
        minutes < 2 -> text(R.string.account_minute_ago)
        minutes < 60 -> text(R.string.account_minutes_ago, "n" to minutes)
        minutes < 120 -> text(R.string.account_hour_ago)
        minutes < 60 * 24 -> text(R.string.account_hours_ago, "n" to minutes / 60)
        minutes < 60 * 48 -> text(R.string.account_day_ago)
        else -> text(R.string.account_days_ago, "n" to minutes / (60 * 24))
    }
}
