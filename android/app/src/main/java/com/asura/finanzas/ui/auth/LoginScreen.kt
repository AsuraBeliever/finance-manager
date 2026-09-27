package com.asura.finanzas.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.cssBoxShadow
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
import androidx.compose.material3.Icon
import com.asura.finanzas.ui.components.Lucide
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

    // `flex min-h-full items-center justify-center px-4 py-10` around a
    // `max-w-sm` column: the card stops at 384 dp on a wide screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 384.dp).fillMaxWidth()) {
        // The web's brand row: the app's own name and its trending-up tile —
        // signed out there is no user appearance to draw from.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    // `shadow-[0_4px_14px_-4px_rgba(22,164,122,0.7)]`
                    .cssBoxShadow(
                        offsetY = 4.dp,
                        blur = 14.dp,
                        spread = (-4).dp,
                        color = Color(0xB316A47A),
                        radius = 12.dp,
                    )
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(colors.accent, colors.accentDim)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.TrendingUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            // `font-display text-2xl font-semibold tracking-tight`
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.6).sp),
                color = colors.fg,
            )
        }
        Spacer(Modifier.height(32.dp))

        // The form lives in a card, heading included — `flex flex-col gap-4
        // rounded-2xl p-6 shadow-card`.
        GlassCard(Modifier.fillMaxWidth(), padding = 24.dp) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // `font-display text-lg font-medium tracking-tight`
            Text(
                text = stringResource(
                    if (registering) R.string.auth_register_title else R.string.auth_login_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = colors.fg,
            )

            // Same order as the web: Google first, then the email form.
            GoogleButton(enabled = !busy, onClick = { signInWithGoogle() })

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HairLine(Modifier.weight(1f))
                Text(
                    stringResource(R.string.auth_or),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgSubtle,
                )
                HairLine(Modifier.weight(1f))
            }

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

            Column {
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
                            imageVector = if (showPassword) Lucide.EyeOff else Lucide.Eye,
                            contentDescription = label,
                            tint = colors.fgSubtle,
                            // `absolute right-0 px-3`: 12 dp from the field's
                            // outer edge, which sits 14 dp past our padded row.
                            modifier = Modifier
                                .offset(x = 2.dp)
                                // No ripple slab: the web's eye only recolours.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { showPassword = !showPassword }
                                .padding(start = 12.dp)
                                // `size={17}` on the web's eye.
                                .size(17.dp),
                        )
                    },
                )
                if (registering) {
                    // `mt-1 text-xs text-fg-subtle`
                    Text(
                        stringResource(R.string.auth_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.fgSubtle,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }

            // The web leaves this live with the fields empty — the browser's
            // own required-field check is what stops the submit — so it reads
            // as the call to action, not a grey slab. `submit()` ignores blanks.
            PrimaryButton(
                text = stringResource(
                    if (registering) R.string.auth_register else R.string.auth_login,
                ),
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            // `text-sm text-fg-muted`
            Text(
                text = stringResource(
                    if (registering) R.string.auth_switch_to_login
                    else R.string.auth_switch_to_register,
                ),
                style = MaterialTheme.typography.bodyMedium,
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            // `px-4 py-2` inside the 1 px border.
            .padding(horizontal = 17.dp, vertical = 9.dp),
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
            // `text-sm font-medium text-stone-800`
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF292524),
        )
    }
}
