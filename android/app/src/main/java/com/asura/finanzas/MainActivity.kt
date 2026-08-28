package com.asura.finanzas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.asura.finanzas.data.AppSettings
import com.asura.finanzas.data.ThemeChoice
import com.asura.finanzas.ui.AppRoot
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.ProvideAppLocale
import com.asura.finanzas.ui.theme.BrokeTheme
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {

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

            ProvideAppLocale(current.locale) {
                CompositionLocalProvider(LocalAppSettings provides current) {
                    BrokeTheme(darkTheme = dark) {
                        AppRoot(
                            repository = app.repository,
                            cookieJar = app.cookieJar,
                            preferences = app.preferences,
                            onReady = { ready = true },
                        )
                    }
                }
            }
        }
    }
}
