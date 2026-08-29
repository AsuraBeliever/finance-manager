package com.asura.finanzas.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.settings.BrandMark
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: BrokeRepository,
    onSignedIn: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = Broke.colors
    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    val context = LocalContext.current
    val googleError = stringResource(R.string.auth_google_error)

    fun signInWithGoogle() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                val idToken = requestGoogleIdToken(context)
                repository.loginWithGoogle(idToken)
            }
                .onSuccess { onSignedIn() }
                .onFailure {
                    error = when (it) {
                        // Dismissing the sheet is a choice, not a failure.
                        is GoogleSignInCancelled -> null
                        is NetworkException -> offlineError
                        else -> it.message ?: googleError
                    }
                    busy = false
                }
        }
    }

    fun submit() {
        if (busy || email.isBlank() || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                if (registering) repository.register(email, password)
                else repository.login(email, password)
            }
                .onSuccess { onSignedIn() }
                .onFailure {
                    error = when (it) {
                        is NetworkException -> offlineError
                        else -> it.message ?: genericError
                    }
                    busy = false
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The brand the user configured, falling back to the app's own name.
        val appearance = LocalAppSettings.current.appearance
        BrandMark(appearance, size = 40.dp)
        Spacer(Modifier.height(8.dp))
        HeroAmount(appearance.appName.ifBlank { stringResource(R.string.app_name) })
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (registering) R.string.auth_register_title else R.string.auth_login_title,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgMuted,
        )
        Spacer(Modifier.height(28.dp))

        // Same order as the web: Google first, then the email form.
        GoogleButton(enabled = !busy, onClick = { signInWithGoogle() })

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HairLine(Modifier.weight(1f))
            Text(
                stringResource(R.string.auth_or),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HairLine(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit() },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text(
                    stringResource(
                        if (registering) R.string.auth_register else R.string.auth_login,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(
                if (registering) R.string.auth_switch_to_login
                else R.string.auth_switch_to_register,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentBright,
            modifier = Modifier.clickable {
                registering = !registering
                error = null
            },
        )
    }
}

/**
 * Google's button, in its own white-on-light treatment rather than the app's
 * palette — the brand guidelines require it, and the web renders it the same
 * way for the same reason.
 */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.auth_continue_with_google),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF1F2937),
        )
    }
}
