package com.asura.finanzas.ui.banknotices

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.asura.finanzas.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

private const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"

/**
 * "Conectar Gmail" on the phone. The web's redirect flow cannot work here (the
 * session cookie lives in the app, not in a browser), so the app asks Google
 * natively for read access to the mailbox and an offline **server auth code**
 * for the web client — the worker trades that code for the refresh token it
 * keeps (`connect_gmail_code`). The app itself never holds a Gmail token.
 *
 * Returns the function that starts the flow. [onCode] gets the code; [onFail]
 * gets null when the user backed out, a message otherwise.
 */
@Composable
fun rememberGmailAuthorization(
    onCode: (String) -> Unit,
    onFail: (String?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onFail(null)
            return@rememberLauncherForActivityResult
        }
        runCatching {
            Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data)
                .serverAuthCode
        }.fold(
            onSuccess = { code -> if (code != null) onCode(code) else onFail(null) },
            onFailure = { onFail(it.message) },
        )
    }
    return {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_READONLY)))
            // forceCodeForRefreshToken: without it a second connect gets a
            // code that carries no refresh token, and pushes stop working.
            .requestOfflineAccess(BuildConfig.GOOGLE_SERVER_CLIENT_ID, true)
            .build()
        Identity.getAuthorizationClient(context.findActivity() ?: context)
            .authorize(request)
            .addOnSuccessListener { auth ->
                val pending = auth.pendingIntent
                if (auth.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    auth.serverAuthCode?.let(onCode) ?: onFail(null)
                }
            }
            .addOnFailureListener { onFail(it.message) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
