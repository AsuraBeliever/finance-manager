package com.asura.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.AppearanceSync
import com.asura.finanzas.data.Outbox
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SessionCookieJar
import com.asura.finanzas.data.UnauthorizedException
import com.asura.finanzas.ui.auth.LoginScreen
import com.asura.finanzas.ui.components.ProvideQueryCache
import com.asura.finanzas.ui.home.HomeScaffold
import com.asura.finanzas.ui.theme.Broke

private enum class AuthState { Checking, SignedOut, SignedIn }

/**
 * Decides between the login screen and the app. The persisted cookie is
 * restored first, then revalidated against the server — the same contract the
 * web app's `["me"]` query has, so a session revoked elsewhere logs the phone
 * out too. Being offline never signs anyone out.
 */
@Composable
fun AppRoot(
    repository: BrokeRepository,
    cookieJar: SessionCookieJar,
    preferences: AppPreferences,
    appearanceSync: AppearanceSync,
    outbox: Outbox,
    onReady: () -> Unit,
) {
    var state by remember { mutableStateOf(AuthState.Checking) }

    LaunchedEffect(Unit) {
        cookieJar.restore()
        state = if (repository.hasStoredSession()) AuthState.SignedIn else AuthState.SignedOut
        onReady()
        if (state == AuthState.SignedIn) {
            runCatching { repository.me() }.onFailure {
                if (it is UnauthorizedException) state = AuthState.SignedOut
            }
        }
    }

    // Whichever way a session ends — the button, or one revoked from another
    // device — the numbers it left behind go with it.
    LaunchedEffect(state) {
        if (state == AuthState.SignedOut) repository.forgetReads()
    }

    // Keyed on the state, not on Unit: this has to run both when the app opens
    // with a session already stored and right after someone signs in. Keying it
    // to first composition only meant a fresh sign-in kept the device's
    // defaults until the next launch.
    LaunchedEffect(state) {
        if (state != AuthState.SignedIn) return@LaunchedEffect
        // Adopt the account's appearance when it is newer than this device's,
        // so a colour picked on the web shows up here.
        runCatching { appearanceSync.pull() }
        // Anything captured without signal goes out as soon as we are back.
        runCatching { repository.flushOutbox() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Broke.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            AuthState.Checking -> Unit
            AuthState.SignedOut -> LoginScreen(
                repository = repository,
                onSignedIn = { state = AuthState.SignedIn },
            )
            // Above the tabs on purpose: the cache has to outlive the screen
            // you just left, which is the whole point of it.
            AuthState.SignedIn -> ProvideQueryCache(repository.queries) {
                HomeScaffold(
                    repository = repository,
                    preferences = preferences,
                    appearanceSync = appearanceSync,
                    outbox = outbox,
                    onSignedOut = { state = AuthState.SignedOut },
                )
            }
        }
    }
}
