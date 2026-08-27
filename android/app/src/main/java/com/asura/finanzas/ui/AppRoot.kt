package com.asura.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SessionCookieJar
import com.asura.finanzas.ui.auth.LoginScreen
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
    onReady: () -> Unit,
) {
    var state by remember { mutableStateOf(AuthState.Checking) }

    LaunchedEffect(Unit) {
        cookieJar.restore()
        state = if (repository.hasStoredSession()) AuthState.SignedIn else AuthState.SignedOut
        onReady()
        // Revalidate in the background; a dead session drops to the login screen.
        if (state == AuthState.SignedIn) {
            runCatching { repository.me() }.onFailure {
                if (it is com.asura.finanzas.data.UnauthorizedException) state = AuthState.SignedOut
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Broke.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            AuthState.Checking -> Text("", color = Broke.colors.fgMuted)
            AuthState.SignedOut -> LoginScreen(
                repository = repository,
                onSignedIn = { state = AuthState.SignedIn },
            )
            AuthState.SignedIn -> HomeScaffold(
                repository = repository,
                onSignedOut = { state = AuthState.SignedOut },
            )
        }
    }
}
