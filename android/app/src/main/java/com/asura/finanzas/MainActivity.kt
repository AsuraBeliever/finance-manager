package com.asura.finanzas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.asura.finanzas.ui.AppRoot
import com.asura.finanzas.ui.theme.BrokeTheme

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
            BrokeTheme {
                AppRoot(
                    repository = app.repository,
                    cookieJar = app.cookieJar,
                    onReady = { ready = true },
                )
            }
        }
    }
}
