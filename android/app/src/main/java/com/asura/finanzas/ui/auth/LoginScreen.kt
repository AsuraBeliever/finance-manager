package com.asura.finanzas.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import com.asura.finanzas.ui.components.GlassCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.FormField
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
    var showPassword by remember { mutableStateOf(false) }
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The brand the user configured, falling back to the app's own name.
        // Tile beside the name, the same header the web draws above the card.
        val appearance = LocalAppSettings.current.appearance
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(colors.accent, colors.accentDim)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BrandMark(appearance, size = 22.dp, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                appearance.appName.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleLarge,
                color = colors.fg,
            )
        }
        Spacer(Modifier.height(32.dp))

        // The form lives in a card, heading included — same as the web, whose
        // `gap-4` is the 16 dp between every row below.
        GlassCard(Modifier.fillMaxWidth(), padding = 24.dp) {
        Text(
            text = stringResource(
                if (registering) R.string.auth_register_title else R.string.auth_login_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = colors.fg,
        )
        Spacer(Modifier.height(16.dp))

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

        FormField(
            label = stringResource(R.string.auth_email),
            value = email,
            onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.auth_email_placeholder),
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(16.dp))

        FormField(
            label = stringResource(R.string.auth_password),
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                val label = stringResource(
                    if (showPassword) R.string.auth_hide_password
                    else R.string.auth_show_password,
                )
                Icon(
            imageVector = if (showPassword) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
            contentDescription = label,
            tint = colors.fgSubtle,
            modifier = Modifier
                        .clickable { showPassword = !showPassword }
                        .padding(horizontal = 12.dp),
                )
            },
        )

        if (registering) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.auth_password_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { submit() },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentDim),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp),
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

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (registering) R.string.auth_switch_to_login
                else R.string.auth_switch_to_register,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = colors.fgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    registering = !registering
                    error = null
                },
        )
        }
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
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_google),
            contentDescription = null,
            // Untinted on purpose: it is Google's mark, not ours to recolour.
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.auth_continue_with_google),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF1F2937),
        )
    }
}
