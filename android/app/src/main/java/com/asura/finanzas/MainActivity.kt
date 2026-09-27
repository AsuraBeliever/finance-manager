package com.asura.finanzas

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.asura.finanzas.data.AppSettings
import com.asura.finanzas.data.ThemeChoice
import com.asura.finanzas.ui.AppRoot
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.ProvideAppLocale
import com.asura.finanzas.ui.theme.BrokeTheme
import java.util.Locale
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {

    /** The language this activity's resources were built in. */
    private var builtLocale: String? = null

    /**
     * The app's language is a setting inside the app, not the phone's. It is
     * applied to the activity itself, not just to the main composition:
     * every dialog and sheet is its own window whose context comes from the
     * activity, and with only a composition-level override they came out in
     * the phone's language (a Spanish app with an English transaction form).
     * Only the locale is overridden, so dark mode and the rest still follow
     * the system.
     */
    override fun attachBaseContext(newBase: Context) {
        val locale = (newBase.applicationContext as BrokeApp).preferences.localeNow()
        builtLocale = locale
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(Configuration().apply { setLocale(Locale.forLanguageTag(locale)) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BrokeApp
        // Hold the splash until the persisted session cookie is loaded, so the
        // app never flashes the login screen at someone who is already signed in.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        setContent {
            val settings by app.preferences.settings.collectAsState(initial = null)
            val current = settings ?: AppSettings("es", ThemeChoice.System, false, true, java.util.TimeZone.getDefault().id)

            val dark = when (current.theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }

            // A new language (chosen in Settings, or brought by the account on
            // sign-in) rebuilds the activity so every window speaks it.
            LaunchedEffect(settings?.locale) {
                val chosen = settings?.locale ?: return@LaunchedEffect
                if (chosen != builtLocale) recreate()
            }

            ProvideAppLocale(current.locale) {
                CompositionLocalProvider(LocalAppSettings provides current) {
                    BrokeTheme(darkTheme = dark, appearance = current.appearance) {
                        AppRoot(
                            repository = app.repository,
                            cookieJar = app.cookieJar,
                            preferences = app.preferences,
                            appearanceSync = app.appearanceSync,
                            outbox = app.outbox,
                            onReady = { ready = true },
                        )
                    }
                }
            }
        }
    }
}
