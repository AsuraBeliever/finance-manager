package com.asura.finanzas.data

import com.asura.finanzas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server rejected the call: `{"error": "..."}` with a 4xx/5xx. */
class ApiException(message: String, val status: Int) : Exception(message)

/** The request never reached the server (offline, DNS, timeout). Retryable. */
class NetworkException(cause: Throwable) : Exception(cause)

/** The session is gone or expired; the app must show the login screen. */
class UnauthorizedException(message: String) : Exception(message)

/**
 * Transport for the backend's RPC API: `POST /api/rpc/<command>` with a
 * camelCase JSON body, mirroring `src/lib/api.ts` in the web app. Every screen
 * goes through here — nothing builds requests by hand.
 *
 * Note the app deliberately sends no `Origin` header: the worker's CSRF check
 * only validates it when present (worker/src/auth: check_origin), which is what
 * lets a native client authenticate with the same cookie the browser uses.
 */
class RpcClient(cookieJar: SessionCookieJar) {

    val json = Json {
        ignoreUnknownKeys = true // the API grows; old builds must keep working
        explicitNulls = false
        encodeDefaults = true
    }

    // Without this every session from the phone lands in the account's device
    // list as "Unknown device": OkHttp's default UA says nothing, and both
    // clients read that list with the same parser (`deviceLabel`).
    private val userAgent =
        "Finanzas/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE}; " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})"

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", userAgent).build(),
            )
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Raw call returning the parsed JSON body, or throwing one of the above. */
    suspend fun call(command: String, args: JsonElement? = null): JsonElement =
        post("/api/rpc/$command", args ?: JsonObject(emptyMap()))

    suspend fun post(path: String, body: JsonElement): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.API_BASE + path)
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(jsonMedia))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException(e)
        }

        response.use {
            val text = it.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (it.isSuccessful) {
                return@withContext parsed ?: JsonObject(emptyMap())
            }
            val message = (parsed as? JsonObject)
                ?.get("error")?.jsonPrimitive?.content
                ?: "Error ${it.code}"
            when (it.code) {
                401 -> throw UnauthorizedException(message)
                else -> throw ApiException(message, it.code)
            }
        }
    }

    suspend fun get(path: String): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BuildConfig.API_BASE + path).get().build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (it.isSuccessful) return@withContext parsed ?: JsonObject(emptyMap())
            val message = (parsed as? JsonObject)?.get("error")?.jsonPrimitive?.content
                ?: "Error ${it.code}"
            if (it.code == 401) throw UnauthorizedException(message)
            throw ApiException(message, it.code)
        }
    }
}

/** Typed convenience over [RpcClient.call]. */
suspend inline fun <reified T> RpcClient.rpc(
    command: String,
    args: JsonElement? = null,
): T = json.decodeFromJsonElement<T>(call(command, args))
