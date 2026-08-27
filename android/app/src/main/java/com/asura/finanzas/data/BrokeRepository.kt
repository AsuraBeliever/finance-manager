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

    suspend fun investmentDetail(id: Long): InvestmentDetail =
        rpc.json.decodeFromJsonElement(
            InvestmentDetail.serializer(),
            rpc.call("get_investment_detail", buildJsonObject { put("id", id) }),
        )

    suspend fun addInvestmentMovement(
        investmentId: Long,
        kind: String,
        amountCents: Long,
        occurredAt: String,
        walletId: Long?,
    ) {
        rpc.call(
            "add_investment_movement",
            buildJsonObject {
                put("investmentId", investmentId)
                put("kind", kind)
                put("amountCents", amountCents)
                put("occurredAt", occurredAt)
                walletId?.let { put("walletId", it) }
            },
        )
        cache.invalidateReads()
    }

    suspend fun addInvestmentSnapshot(investmentId: Long, valueCents: Long, asOf: String) {
        rpc.call(
            "add_snapshot",
            buildJsonObject {
                put("investmentId", investmentId)
                put("valueCents", valueCents)
                put("asOf", asOf)
            },
        )
        cache.invalidateReads()
    }

    suspend fun closeInvestment(id: Long) {
        rpc.call("close_investment", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun deleteInvestment(id: Long) {
        rpc.call("delete_investment", buildJsonObject { put("id", id) })
        cache.invalidateReads()
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

    suspend fun transactions(
        limit: Int = 100,
        walletId: Long? = null,
        kind: String? = null,
        period: JsonObject? = null,
    ): Synced<List<Transaction>> {
        // Only the unfiltered list is worth keeping for offline: a cache keyed
        // by every filter combination would mostly be misses.
        val key = if (walletId == null && kind == null && period == null) "transactions" else null
        return cached(key, ListSerializer(Transaction.serializer())) {
            rpc.call(
                "list_transactions",
                buildJsonObject {
                    // limit/offset live inside the filter (worker: TxFilter).
                    putJsonObject("filter") {
                        put("limit", limit)
                        put("offset", 0)
                        walletId?.let { put("walletId", it) }
                        kind?.let { put("kind", it) }
                        period?.let { put("period", it) }
                    }
                },
            )
        }
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

    // ---- wallets ----

    suspend fun walletCategories(): List<WalletCategory> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(WalletCategory.serializer()),
            rpc.call("list_wallet_categories"),
        )

    suspend fun currencies(): List<Currency> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(Currency.serializer()),
            rpc.call("list_currencies"),
        )

    suspend fun saveWallet(
        id: Long?,
        name: String,
        categoryId: Long,
        currencyCode: String,
        initialBalanceCents: Long,
        color: String?,
        skin: String?,
        notes: String?,
        yieldRateBps: Long?,
        yieldFrequency: String?,
        parentWalletId: Long?,
    ) {
        val body = buildJsonObject {
            id?.let { put("id", it) }
            put("name", name.trim())
            put("categoryId", categoryId)
            put("currencyCode", currencyCode)
            put("initialBalanceCents", initialBalanceCents)
            color?.let { put("color", it) }
            skin?.let { put("skin", it) }
            notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            yieldRateBps?.let { put("yieldRateBps", it) }
            yieldFrequency?.let { put("yieldFrequency", it) }
            parentWalletId?.let { put("parentWalletId", it) }
        }
        rpc.call(if (id == null) "create_wallet" else "update_wallet", body)
        cache.invalidateReads()
    }

    suspend fun archiveWallet(id: Long, archived: Boolean) {
        rpc.call(
            "archive_wallet",
            buildJsonObject { put("id", id); put("archived", archived) },
        )
        cache.invalidateReads()
    }

    suspend fun deleteWallet(id: Long) {
        rpc.call("delete_wallet", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    // ---- planning ----

    suspend fun saveGoal(
        id: Long?,
        name: String,
        currencyCode: String,
        targetCents: Long,
        walletId: Long?,
        color: String?,
        targetDate: String?,
        cadence: String?,
        goalKind: String,
    ) {
        val body = buildJsonObject {
            id?.let { put("id", it) }
            put("name", name.trim())
            put("currencyCode", currencyCode)
            put("targetCents", targetCents)
            walletId?.let { put("walletId", it) }
            color?.let { put("color", it) }
            targetDate?.let { put("targetDate", it) }
            cadence?.let { put("cadence", it) }
            put("goalKind", goalKind)
        }
        rpc.call(if (id == null) "create_savings_goal" else "update_savings_goal", body)
        cache.invalidateReads()
    }

    /** Positive reserves more, negative releases. The server clamps at zero. */
    suspend fun contributeToGoal(id: Long, amountCents: Long) {
        rpc.call(
            "contribute_savings_goal",
            buildJsonObject { put("id", id); put("amountCents", amountCents) },
        )
        cache.invalidateReads()
    }

    suspend fun deleteGoal(id: Long) {
        rpc.call("delete_savings_goal", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun setBudget(categoryId: Long?, limitCents: Long) {
        rpc.call(
            "set_budget",
            buildJsonObject {
                categoryId?.let { put("categoryId", it) }
                put("limitCents", limitCents)
            },
        )
        cache.invalidateReads()
    }

    suspend fun deleteBudget(id: Long) {
        rpc.call("delete_budget", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun saveSubscription(
        id: Long?,
        name: String,
        amountCents: Long,
        currencyCode: String,
        cadence: String,
        nextChargeDate: String,
        walletId: Long?,
        categoryId: Long?,
        color: String?,
    ) {
        val body = buildJsonObject {
            id?.let { put("id", it) }
            put("name", name.trim())
            put("amountCents", amountCents)
            put("currencyCode", currencyCode)
            put("cadence", cadence)
            put("nextChargeDate", nextChargeDate)
            walletId?.let { put("walletId", it) }
            categoryId?.let { put("categoryId", it) }
            color?.let { put("color", it) }
        }
        rpc.call(if (id == null) "create_subscription" else "update_subscription", body)
        cache.invalidateReads()
    }

    suspend fun setSubscriptionActive(id: Long, active: Boolean) {
        rpc.call(
            "set_subscription_active",
            buildJsonObject { put("id", id); put("active", active) },
        )
        cache.invalidateReads()
    }

    /** Posts the charge as a real expense from the subscription's wallet. */
    suspend fun registerSubscriptionPayment(id: Long) {
        rpc.call("register_subscription_payment", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun deleteSubscription(id: Long) {
        rpc.call("delete_subscription", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun createCategory(name: String, kind: String, color: String?) {
        rpc.call(
            "create_transaction_category",
            buildJsonObject {
                put("name", name.trim())
                put("kind", kind)
                color?.let { put("color", it) }
            },
        )
        cache.invalidateReads()
    }

    suspend fun updateCategory(id: Long, name: String, color: String?) {
        rpc.call(
            "update_transaction_category",
            buildJsonObject {
                put("id", id)
                put("name", name.trim())
                color?.let { put("color", it) }
            },
        )
        cache.invalidateReads()
    }

    suspend fun deleteCategory(id: Long) {
        rpc.call("delete_transaction_category", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    suspend fun restoreCategory(id: Long) {
        rpc.call("restore_transaction_category", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    private suspend fun <T> cached(
        key: String?,
        serializer: kotlinx.serialization.KSerializer<T>,
        fetch: suspend () -> kotlinx.serialization.json.JsonElement,
    ): Synced<T> = try {
        val element = fetch()
        if (key != null) {
            cache.write(
                key,
                rpc.json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element),
            )
        }
        Synced(rpc.json.decodeFromJsonElement(serializer, element), fromCache = false)
    } catch (offline: NetworkException) {
        val stored = key?.let { cache.read(it) } ?: throw offline
        Synced(rpc.json.decodeFromString(serializer, stored), fromCache = true)
    }
}
