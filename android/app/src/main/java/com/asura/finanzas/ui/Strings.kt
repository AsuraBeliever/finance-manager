package com.asura.finanzas.ui

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.asura.finanzas.data.AppSettings
import com.asura.finanzas.data.ThemeChoice
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** Device-local settings (language, theme, privacy) available to every screen. */
val LocalAppSettings: ProvidableCompositionLocal<AppSettings> =
    staticCompositionLocalOf { AppSettings("es", ThemeChoice.System, false, true, java.util.TimeZone.getDefault().id) }

/**
 * UI strings come from the `strings_i18n.xml` under each `res/values` folder,
 * which is **generated** from the web app's `src/i18n/` dictionaries by
 * `npm run gen:android-strings`. Never edit those XML files or hardcode Spanish
 * in a composable: add the key in `src/i18n/es.ts` + `en.ts`, regenerate, and
 * both apps stay in step by construction.
 */
@Composable
fun text(id: Int): String = stringResource(id)

/**
 * The dictionaries interpolate with `{name}` placeholders, which Android's own
 * format specifiers don't understand — so substitution happens here, matching
 * what the web does.
 */
@Composable
fun text(id: Int, vararg args: Pair<String, Any?>): String {
    var value = stringResource(id)
    for ((key, replacement) in args) {
        value = value.replace("{$key}", replacement?.toString().orEmpty())
    }
    return value
}

/** Same, outside composition (view models, formatters). */
fun Context.text(id: Int, vararg args: Pair<String, Any?>): String {
    var value = getString(id)
    for ((key, replacement) in args) {
        value = value.replace("{$key}", replacement?.toString().orEmpty())
    }
    return value
}

/**
 * Language is a setting inside the app, like on the web — it does not just
 * follow the system. Overriding the context's configuration makes every
 * `stringResource` below resolve against the chosen locale.
 */
@Composable
fun ProvideAppLocale(locale: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized = remember(locale, configuration) {
        val updated = Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(locale))
        }
        context.createConfigurationContext(updated)
    }

    // Swapping LocalContext for a configuration context loses the trail back to
    // the Activity, and that is how `rememberLauncherForActivityResult` finds
    // the result registry — so anything that opens the photo picker (a wallet
    // skin, the app logo) crashed. Carry the owner across by hand.
    val registryOwner = LocalActivityResultRegistryOwner.current

    val locals = buildList {
        add(LocalContext provides localized)
        add(LocalConfiguration provides localized.resources.configuration)
        registryOwner?.let { add(LocalActivityResultRegistryOwner provides it) }
    }.toTypedArray()

    CompositionLocalProvider(values = locals, content = content)
}
