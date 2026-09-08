package com.asura.finanzas.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.asura.finanzas.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** The user dismissed the sheet: not an error worth showing. */
class GoogleSignInCancelled : Exception()

/** No Google credential came back — nothing to trade for a session. */
class NoGoogleCredential : Exception("sin credencial de Google")

/**
 * Ask Credential Manager for a Google ID token.
 *
 * `serverClientId` is the **web** client id on purpose: Google stamps it as the
 * token's audience, and that is what the worker checks before trusting it. The
 * token is useless to anyone else, and the app never sees a password.
 *
 * First pass filters to accounts already used with this app; if none match, a
 * second pass offers every account on the device — the same "sign in, or sign
 * up" shape the web's consent screen has.
 */
suspend fun requestGoogleIdToken(context: Context): String {
    val manager = CredentialManager.create(context)

    suspend fun attempt(filterByAuthorized: Boolean): String? {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setFilterByAuthorizedAccounts(filterByAuthorized)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = manager.getCredential(context, request)
        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    return try {
        attempt(filterByAuthorized = true) ?: throw NoGoogleCredential()
    } catch (_: GetCredentialCancellationException) {
        throw GoogleSignInCancelled()
    } catch (_: GetCredentialException) {
        // Nothing previously authorized — offer every account on the device.
        try {
            attempt(filterByAuthorized = false) ?: throw NoGoogleCredential()
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        }
    }
}
