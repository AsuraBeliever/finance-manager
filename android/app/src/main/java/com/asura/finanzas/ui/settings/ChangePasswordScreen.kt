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
import androidx.compose.material3.OutlinedTextField
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
            .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(stringResource(R.string.account_change_password), onBack)

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.account_password_change_note),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
        }

        OutlinedTextField(
            value = current,
            onValueChange = { current = it; error = null },
            label = { Text(stringResource(R.string.account_current_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = next,
            onValueChange = { next = it; error = null },
            label = { Text(stringResource(R.string.account_new_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = { Text(stringResource(R.string.account_confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger) }
        if (done) {
            Text(
                stringResource(R.string.account_password_changed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.positive,
            )
        }

        Spacer(Modifier.height(4.dp))
        if (busy) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
        } else {
            PrimaryButton(
                text = stringResource(R.string.common_save),
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
