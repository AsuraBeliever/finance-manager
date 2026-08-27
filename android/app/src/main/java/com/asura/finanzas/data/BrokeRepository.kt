package com.asura.finanzas.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A value plus whether it came from the network or from the offline cache. */
data class Synced<T>(val value: T, val fromCache: Boolean)

/**
 * The app's one door to the backend. Screens ask for domain objects; this class
 * decides whether that means a network call or the last-synced copy.
 *
 * Reads fall back to cache when the request never reached the server. Real API
 * errors (a 400, a 404) are not swallowed — only [NetworkException] is, because
 * only that one means "no signal", not "the server said no".
 */
class BrokeRepository(
    private val rpc: RpcClient,
    private val cache: JsonCache,
    private val cookieJar: SessionCookieJar,
) {

    // ---- session ----

    suspend fun login(email: String, password: String): User {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        val element = rpc.post("/api/auth/login", body)
        return rpc.json.decodeFromJsonElement(User.serializer(), element)
    }

    suspend fun me(): User {
        val element = rpc.get("/api/auth/me")
        return rpc.json.decodeFromJsonElement(User.serializer(), element)
    }

    suspend fun logout() {
        runCatching { rpc.post("/api/auth/logout", JsonObject(emptyMap())) }
        cookieJar.clear()
        cache.clear()
    }

    fun hasStoredSession(): Boolean = cookieJar.hasSession()

    // ---- reads ----

    suspend fun dashboard(): Synced<DashboardSummary> =
        cached("dashboard", DashboardSummary.serializer()) {
            rpc.call("get_dashboard_summary")
        }

    suspend fun wallets(): Synced<List<Wallet>> =
        cached("wallets", ListSerializer(Wallet.serializer())) {
            rpc.call("list_wallets")
        }

    suspend fun transactions(limit: Int = 50): Synced<List<Transaction>> =
        cached("transactions", ListSerializer(Transaction.serializer())) {
            rpc.call(
                "list_transactions",
                buildJsonObject {
                    // limit/offset live inside the filter (worker: TxFilter).
                    putJsonObject("filter") {
                        put("limit", limit)
                        put("offset", 0)
                    }
                },
            )
        }

    private suspend fun <T> cached(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        fetch: suspend () -> kotlinx.serialization.json.JsonElement,
    ): Synced<T> = try {
        val element = fetch()
        cache.write(key, rpc.json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element))
        Synced(rpc.json.decodeFromJsonElement(serializer, element), fromCache = false)
    } catch (offline: NetworkException) {
        val stored = cache.read(key) ?: throw offline
        Synced(rpc.json.decodeFromString(serializer, stored), fromCache = true)
    }
}
