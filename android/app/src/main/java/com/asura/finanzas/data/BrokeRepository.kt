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

    suspend fun savingsGoals(): Synced<List<SavingsGoal>> =
        cached("goals", ListSerializer(SavingsGoal.serializer())) {
            rpc.call("list_savings_goals")
        }

    suspend fun budgets(): Synced<List<Budget>> =
        cached("budgets", ListSerializer(Budget.serializer())) { rpc.call("list_budgets") }

    suspend fun subscriptions(): Synced<SubscriptionList> =
        cached("subscriptions", SubscriptionList.serializer()) { rpc.call("list_subscriptions") }

    suspend fun manageCategories(): Synced<List<TransactionCategory>> =
        cached("categories", ListSerializer(TransactionCategory.serializer())) {
            rpc.call("list_manage_categories")
        }

    suspend fun investments(): Synced<List<Investment>> =
        cached("investments", ListSerializer(Investment.serializer())) {
            rpc.call("list_investments")
        }

    suspend fun portfolio(): Synced<Portfolio> =
        cached("portfolio", Portfolio.serializer()) { rpc.call("get_portfolio") }

    suspend fun spendingTrends(period: JsonObject): Synced<SpendingTrends> =
        cached("trends", SpendingTrends.serializer()) {
            rpc.call("get_spending_trends", buildJsonObject { put("period", period) })
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

    suspend fun transactionCategories(kind: String): List<TransactionCategory> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(TransactionCategory.serializer()),
            rpc.call("list_transaction_categories", buildJsonObject { put("kind", kind) }),
        )

    // ---- writes ----
    //
    // The three capture commands accept a clientId the server uses for
    // idempotency (unique index on transactions.client_id), so a retry after a
    // lost response can never double-post. That is what an offline outbox will
    // build on; today it already makes a flaky-network retry safe.

    suspend fun addIncome(
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
        clientId: String,
    ) = addSimple("add_income", walletId, amountCents, occurredAt, categoryId, description, occurredTime, clientId)

    suspend fun addExpense(
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
        clientId: String,
    ) = addSimple("add_expense", walletId, amountCents, occurredAt, categoryId, description, occurredTime, clientId)

    private suspend fun addSimple(
        command: String,
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
        clientId: String,
    ) {
        rpc.call(
            command,
            buildJsonObject {
                put("walletId", walletId)
                put("amountCents", amountCents)
                put("occurredAt", occurredAt)
                categoryId?.let { put("categoryId", it) }
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                occurredTime?.let { put("occurredTime", it) }
                put("clientId", clientId)
            },
        )
        cache.invalidateReads()
    }

    suspend fun addTransfer(
        fromWalletId: Long,
        toWalletId: Long,
        amountFromCents: Long,
        amountToCents: Long,
        occurredAt: String,
        description: String?,
        occurredTime: String?,
        clientId: String,
    ) {
        rpc.call(
            "add_transfer",
            buildJsonObject {
                put("fromWalletId", fromWalletId)
                put("toWalletId", toWalletId)
                put("amountFromCents", amountFromCents)
                put("amountToCents", amountToCents)
                put("occurredAt", occurredAt)
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                occurredTime?.let { put("occurredTime", it) }
                put("clientId", clientId)
            },
        )
        cache.invalidateReads()
    }

    suspend fun deleteTransaction(id: Long) {
        rpc.call("delete_transaction", buildJsonObject { put("id", id) })
        cache.invalidateReads()
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
