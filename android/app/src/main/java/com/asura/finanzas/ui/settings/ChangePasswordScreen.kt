package com.asura.finanzas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.FieldHint
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * Changing the password revokes every other session server-side, so the phone
 * keeps its own and the user stays signed in here.
 */
@Composable
fun ChangePasswordScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val scope = rememberCoroutineScope()

    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val mismatch = stringResource(R.string.account_password_mismatch)

    val canSave = !busy && current.isNotBlank() && next.isNotBlank() && confirm.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(
            stringResource(R.string.account_change_password),
            onBack,
            // "Back to settings", the wording the web's link uses here.
            backLabel = stringResource(R.string.settings_back),
        )

        // The web keeps the whole form — fields, note and button — inside one
        // card; loose fields with the note floated to the top read as a
        // different screen.
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormField(
            label = stringResource(R.string.account_current_password),
            value = current,
            onValueChange = { current = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        FormField(
            label = stringResource(R.string.account_new_password),
            value = next,
            onValueChange = { next = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        FormField(
            label = stringResource(R.string.account_confirm_password),
            value = confirm,
            onValueChange = { confirm = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )

        FieldHint(stringResource(R.string.account_password_change_note))

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
        if (done) {
            Text(
                stringResource(R.string.account_password_changed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.positive,
            )
        }

        if (busy) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
        } else {
            PrimaryButton(
                // The web's button says what it does, and is only as wide as
                // its label.
                text = stringResource(R.string.account_change_password),
                enabled = canSave,
                onClick = {
                    if (next != confirm) {
                        error = mismatch
                        return@PrimaryButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { repository.changePassword(current, next) }
                            .onSuccess {
                                done = true
                                busy = false
                                current = ""; next = ""; confirm = ""
                            }
                            .onFailure {
                                error = if (it is NetworkException) offlineError
                                else it.message ?: genericError
                                busy = false
                            }
                    }
                },
            )
        }
        }
        }
    }
}
