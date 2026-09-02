package com.asura.finanzas.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val outbox: Outbox,
) {

    /**
     * What each read last returned, so leaving a tab and coming back repaints
     * at once instead of starting over. Lives here so it dies with the session,
     * like every other piece of the user's data.
     */
    val queries = QueryCache()

    // ---- session ----

    suspend fun login(email: String, password: String): User {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        val element = rpc.post("/api/auth/login", body)
        return rpc.json.decodeFromJsonElement(User.serializer(), element)
    }

    suspend fun register(email: String, password: String): User {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        val element = rpc.post("/api/auth/register", body)
        return rpc.json.decodeFromJsonElement(User.serializer(), element)
    }

    /**
     * Trade a Google ID token for a session. The worker verifies the token with
     * Google and checks it was issued for this project before honouring it, so
     * nothing here is trusted on the phone's word.
     */
    suspend fun loginWithGoogle(idToken: String): User {
        val element = rpc.post(
            "/api/auth/google/token",
            buildJsonObject { put("idToken", idToken) },
        )
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
        queries.clear()
        outbox.clear()
    }

    /** Revokes every other session server-side; this device keeps its own. */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        rpc.post(
            "/api/auth/change_password",
            buildJsonObject {
                put("currentPassword", currentPassword)
                put("newPassword", newPassword)
            },
        )
    }

    suspend fun sessions(): List<SessionInfo> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(SessionInfo.serializer()),
            rpc.get("/api/auth/sessions"),
        )

    suspend fun revokeSession(id: Long) {
        rpc.post("/api/auth/revoke_session", buildJsonObject { put("id", id) })
    }

    suspend fun revokeOtherSessions() {
        rpc.post("/api/auth/revoke_other_sessions", JsonObject(emptyMap()))
    }

    fun hasStoredSession(): Boolean = cookieJar.hasSession()

    /** Raw account setting; null when unset. */
    suspend fun getSetting(key: String): String? = runCatching {
        val element = rpc.call("get_setting", buildJsonObject { put("key", key) })
        (element as? JsonPrimitive)?.takeIf { !it.isString || it.content.isNotEmpty() }?.content
    }.getOrNull()

    suspend fun setSetting(key: String, value: String) {
        rpc.call("set_setting", buildJsonObject { put("key", key); put("value", value) })
    }

    /**
     * The release version currently deployed, from the same `version.json` the
     * web uses to spot a new build. Null when it cannot be read — an update
     * check is never worth surfacing an error for.
     */
    suspend fun deployedAppVersion(): String? = runCatching {
        (rpc.get("/version.json") as? JsonObject)
            ?.get("appVersion")
            ?.let { rpc.json.decodeFromJsonElement(String.serializer(), it) }
    }.getOrNull()

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

    suspend fun investments(includeClosed: Boolean = false): Synced<List<Investment>> =
        cached(if (includeClosed) null else "investments", ListSerializer(Investment.serializer())) {
            rpc.call(
                "list_investments",
                buildJsonObject { put("includeClosed", includeClosed) },
            )
        }

    /**
     * What a plan would grow into. Stateless: nothing is read from or written to
     * the account, and the compounding happens in Rust.
     */
    suspend fun simulateInvestment(
        initialCents: Long,
        contributionCents: Long,
        cadence: String,
        annualRateBps: Long,
        months: Int,
    ): SimResult = rpc.json.decodeFromJsonElement(
        SimResult.serializer(),
        rpc.call(
            "simulate_investment",
            buildJsonObject {
                put("initialCents", initialCents)
                put("contributionCents", contributionCents)
                put("cadence", cadence)
                put("annualRateBps", annualRateBps)
                put("months", months)
            },
        ),
    )

    /** The monthly contribution that reaches a target in a given time. */
    suspend fun solveContribution(
        initialCents: Long,
        targetCents: Long,
        annualRateBps: Long,
        months: Int,
    ): SolveResult = rpc.json.decodeFromJsonElement(
        SolveResult.serializer(),
        rpc.call(
            "solve_contribution",
            buildJsonObject {
                put("initialCents", initialCents)
                put("targetCents", targetCents)
                put("annualRateBps", annualRateBps)
                put("months", months)
            },
        ),
    )

    suspend fun investmentCatalog(): List<CatalogItem> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(CatalogItem.serializer()),
            rpc.call("get_investment_catalog"),
        )

    suspend fun createInvestment(
        calculator: String,
        name: String,
        currencyCode: String,
        principalCents: Long,
        startDate: String,
        paramsJson: String,
        linkedWalletId: Long?,
        notes: String?,
    ) {
        rpc.call(
            "create_investment",
            buildJsonObject {
                put("calculator", calculator)
                put("name", name.trim())
                put("currencyCode", currencyCode)
                put("principalCents", principalCents)
                put("startDate", startDate)
                put("paramsJson", paramsJson)
                linkedWalletId?.let { put("linkedWalletId", it) }
                notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            },
        )
        cache.invalidateReads()
    }

    suspend fun investmentDetail(id: Long): InvestmentDetail =
        rpc.json.decodeFromJsonElement(
            InvestmentDetail.serializer(),
            rpc.call("get_investment_detail", buildJsonObject { put("id", id) }),
        )

    /**
     * Edit an existing investment. The calculator and its params travel too,
     * because changing the rate of a CETES is a legitimate edit — the server
     * revalues from them.
     */
    suspend fun updateInvestment(
        id: Long,
        name: String,
        currencyCode: String,
        principalCents: Long,
        startDate: String,
        paramsJson: String,
        linkedWalletId: Long?,
        notes: String?,
    ) {
        rpc.call(
            "update_investment",
            buildJsonObject {
                put("id", id)
                put("name", name.trim())
                put("currencyCode", currencyCode)
                put("principalCents", principalCents)
                put("startDate", startDate)
                put("paramsJson", paramsJson)
                linkedWalletId?.let { put("linkedWalletId", it) }
                notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            },
        )
        cache.invalidateReads()
    }

    /** One movement with the wallet leg the list shape does not carry. */
    suspend fun investmentMovement(id: Long): MovementDetail =
        rpc.json.decodeFromJsonElement(
            MovementDetail.serializer(),
            rpc.call("get_investment_movement", buildJsonObject { put("id", id) }),
        )

    /**
     * The same movement addressed by its wallet transfer leg, which is all the
     * transactions list knows about it.
     */
    suspend fun investmentMovementByTransaction(transactionId: Long): MovementDetail =
        rpc.json.decodeFromJsonElement(
            MovementDetail.serializer(),
            rpc.call(
                "get_investment_movement",
                buildJsonObject { put("transactionId", transactionId) },
            ),
        )

    suspend fun updateInvestmentMovement(
        id: Long,
        kind: String,
        amountCents: Long,
        occurredAt: String,
        walletId: Long?,
    ) {
        rpc.call(
            "update_investment_movement",
            buildJsonObject {
                put("id", id)
                put("kind", kind)
                put("amountCents", amountCents)
                put("occurredAt", occurredAt)
                walletId?.let { put("walletId", it) }
            },
        )
        cache.invalidateReads()
    }

    suspend fun deleteInvestmentMovement(id: Long) {
        rpc.call("delete_investment_movement", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

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

    /**
     * Re-runs the projection over a horizon, optionally with imagined recurring
     * contributions. Every figure is finanzas-core's; this only asks for them.
     */
    suspend fun projectInvestment(
        id: Long,
        months: Int,
        contributionCents: Long = 0,
        cadence: String = "none",
    ): InvestmentProjection = rpc.json.decodeFromJsonElement(
        InvestmentProjection.serializer(),
        rpc.call(
            "project_investment",
            buildJsonObject {
                put("id", id)
                put("months", months)
                put("contributionCents", contributionCents)
                put("cadence", cadence)
            },
        ),
    )

    suspend fun closeInvestment(id: Long, closed: Boolean) {
        rpc.call(
            "close_investment",
            buildJsonObject { put("id", id); put("closed", closed) },
        )
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

    suspend fun categoryBreakdown(kind: String, period: JsonObject): Synced<CategoryBreakdown> =
        cached("breakdown-$kind", CategoryBreakdown.serializer()) {
            rpc.call(
                "get_category_breakdown",
                buildJsonObject { put("kind", kind); put("period", period) },
            )
        }

    /**
     * Archived wallets are excluded by default. Without asking for them there
     * is no way back: archiving one would put it out of reach for good.
     */
    suspend fun wallets(includeArchived: Boolean = false): Synced<List<Wallet>> =
        cached(if (includeArchived) null else "wallets", ListSerializer(Wallet.serializer())) {
            rpc.call(
                "list_wallets",
                buildJsonObject { put("includeArchived", includeArchived) },
            )
        }

    suspend fun transactions(
        limit: Int = 100,
        walletId: Long? = null,
        kind: String? = null,
        categoryId: Long? = null,
        period: JsonObject? = null,
    ): Synced<List<Transaction>> {
        // Only the unfiltered list is worth keeping for offline: a cache keyed
        // by every filter combination would mostly be misses.
        val key = if (walletId == null && kind == null && categoryId == null && period == null) {
            "transactions"
        } else {
            null
        }
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
                        categoryId?.let { put("categoryId", it) }
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

    /**
     * Every category a movement may already point at, including the reserved
     * ones the pickers hide (goal contributions, MSI instalments). Mirrors the
     * web's `listFilterCategories`: a filter has to be able to name a category
     * that capture forms will not offer.
     */
    suspend fun filterCategories(): List<TransactionCategory> =
        rpc.json.decodeFromJsonElement(
            ListSerializer(TransactionCategory.serializer()),
            rpc.call(
                "list_transaction_categories",
                buildJsonObject { put("includeReserved", true) },
            ),
        )

    // ---- writes ----
    //
    // The three capture commands go through the outbox: it sends them, and only
    // queues when the request never reached the server. Each carries a clientId
    // the server uses for idempotency (unique index on transactions.client_id),
    // so replaying after a dropped response can never double-post.
    //
    // They return true when the movement was queued rather than sent, which is
    // what the form tells the user.

    suspend fun addIncome(
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
    ): Boolean = addSimple(
        "add_income", walletId, amountCents, occurredAt, categoryId, description, occurredTime,
    )

    suspend fun addExpense(
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
    ): Boolean = addSimple(
        "add_expense", walletId, amountCents, occurredAt, categoryId, description, occurredTime,
    )

    private suspend fun addSimple(
        command: String,
        walletId: Long,
        amountCents: Long,
        occurredAt: String,
        categoryId: Long?,
        description: String?,
        occurredTime: String?,
    ): Boolean {
        val queued = outbox.submitOrQueue(
            command,
            buildJsonObject {
                put("walletId", walletId)
                put("amountCents", amountCents)
                put("occurredAt", occurredAt)
                categoryId?.let { put("categoryId", it) }
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                occurredTime?.let { put("occurredTime", it) }
            },
        )
        if (!queued) cache.invalidateReads()
        return queued
    }

    suspend fun addTransfer(
        fromWalletId: Long,
        toWalletId: Long,
        amountFromCents: Long,
        amountToCents: Long,
        occurredAt: String,
        description: String?,
        occurredTime: String?,
    ): Boolean {
        val queued = outbox.submitOrQueue(
            "add_transfer",
            buildJsonObject {
                put("fromWalletId", fromWalletId)
                put("toWalletId", toWalletId)
                put("amountFromCents", amountFromCents)
                put("amountToCents", amountToCents)
                put("occurredAt", occurredAt)
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                occurredTime?.let { put("occurredTime", it) }
            },
        )
        if (!queued) cache.invalidateReads()
        return queued
    }

    /** Drain whatever the outbox is still holding; returns how many synced. */
    suspend fun flushOutbox(): Int {
        val synced = outbox.flush()
        if (synced > 0) cache.invalidateReads()
        return synced
    }

    /** Totals for a filtered slice; only meaningful for income or expense. */
    suspend fun transactionTotals(
        kind: String,
        walletId: Long?,
        categoryId: Long?,
        period: JsonObject?,
    ): TxTotals = rpc.json.decodeFromJsonElement(
        TxTotals.serializer(),
        rpc.call(
            "sum_transactions",
            buildJsonObject {
                putJsonObject("filter") {
                    put("kind", kind)
                    walletId?.let { put("walletId", it) }
                    categoryId?.let { put("categoryId", it) }
                    period?.let { put("period", it) }
                }
            },
        ),
    )

    suspend fun updateTransaction(
        id: Long,
        walletId: Long,
        amountCents: Long,
        categoryId: Long?,
        description: String?,
        occurredAt: String,
        occurredTime: String?,
    ) {
        rpc.call(
            "update_transaction",
            buildJsonObject {
                put("id", id)
                put("walletId", walletId)
                put("amountCents", amountCents)
                categoryId?.let { put("categoryId", it) }
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                put("occurredAt", occurredAt)
                occurredTime?.let { put("occurredTime", it) }
            },
        )
        cache.invalidateReads()
    }

    /**
     * The movements behind one breakdown slice, over the same period the widget
     * is showing. `categoryId` null means the "uncategorized" slice.
     */
    suspend fun categoryTransactions(
        kind: String,
        categoryId: Long?,
        period: JsonObject,
    ): List<Transaction> = rpc.json.decodeFromJsonElement(
        ListSerializer(Transaction.serializer()),
        rpc.call(
            "get_category_transactions",
            buildJsonObject {
                put("kind", kind)
                categoryId?.let { put("categoryId", it) }
                put("period", period)
            },
        ),
    )

    /** Reads a whole transfer from any one of its two leg ids. */
    suspend fun getTransfer(id: Long): TransferDetail = rpc.json.decodeFromJsonElement(
        TransferDetail.serializer(),
        rpc.call("get_transfer", buildJsonObject { put("id", id) }),
    )

    /** Edits both legs at once; the worker keeps them in one atomic batch. */
    suspend fun updateTransfer(
        id: Long,
        fromWalletId: Long,
        toWalletId: Long,
        amountFromCents: Long,
        amountToCents: Long,
        description: String?,
        occurredAt: String,
        occurredTime: String?,
    ) {
        rpc.call(
            "update_transfer",
            buildJsonObject {
                put("id", id)
                put("fromWalletId", fromWalletId)
                put("toWalletId", toWalletId)
                put("amountFromCents", amountFromCents)
                put("amountToCents", amountToCents)
                description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                put("occurredAt", occurredAt)
                occurredTime?.let { put("occurredTime", it) }
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

    suspend fun exchangeRates(): Synced<List<ExchangeRate>> =
        cached("rates", ListSerializer(ExchangeRate.serializer())) {
            rpc.call("get_exchange_rates")
        }

    /** Ask the server to refresh rates from its market sources. */
    suspend fun fetchExchangeRates() {
        rpc.call("fetch_exchange_rates")
        cache.invalidateReads()
    }

    /**
     * Pin a rate by hand. The worker stores it against this user, where it wins
     * over the auto-fetched global one — for this account only. Micros are built
     * here from the typed decimal only to shape the request; nothing about a
     * balance is computed on the phone.
     */
    suspend fun setExchangeRate(currencyCode: String, rateToMxnMicros: Long) {
        rpc.call(
            "set_exchange_rate",
            buildJsonObject {
                put("currencyCode", currencyCode)
                put("rateToMxnMicros", rateToMxnMicros)
            },
        )
        cache.invalidateReads()
    }

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

    suspend fun wallet(id: Long): Wallet =
        rpc.json.decodeFromJsonElement(
            Wallet.serializer(),
            rpc.call("get_wallet", buildJsonObject { put("id", id) }),
        )

    suspend fun creditCardSummary(walletId: Long): CreditCardSummary =
        rpc.json.decodeFromJsonElement(
            CreditCardSummary.serializer(),
            rpc.call("get_credit_card_summary", buildJsonObject { put("walletId", walletId) }),
        )

    /**
     * The schedule an MSI plan would produce, without writing anything — the
     * live "≈ $X/mes · primer cargo el…" line under the form.
     */
    suspend fun previewMsiPlan(
        walletId: Long,
        totalCents: Long,
        months: Int,
        purchasedAt: String?,
    ): MsiSchedulePreview = rpc.json.decodeFromJsonElement(
        MsiSchedulePreview.serializer(),
        rpc.call(
            "preview_msi_plan",
            buildJsonObject {
                put("walletId", walletId)
                put("description", "")
                put("totalCents", totalCents)
                put("months", months)
                purchasedAt?.let { put("purchasedAt", it) }
            },
        ),
    )

    /** Creates the plan and returns the same schedule the preview showed. */
    suspend fun createMsiPlan(
        walletId: Long,
        description: String,
        totalCents: Long,
        months: Int,
        purchasedAt: String?,
        categoryId: Long?,
    ): MsiSchedulePreview {
        val result = rpc.json.decodeFromJsonElement(
            MsiSchedulePreview.serializer(),
            rpc.call(
                "create_msi_plan",
                buildJsonObject {
                    put("walletId", walletId)
                    put("description", description.trim())
                    put("totalCents", totalCents)
                    put("months", months)
                    purchasedAt?.let { put("purchasedAt", it) }
                    categoryId?.let { put("categoryId", it) }
                },
            ),
        )
        cache.invalidateReads()
        return result
    }

    suspend fun deleteMsiPlan(id: Long) {
        rpc.call("delete_msi_plan", buildJsonObject { put("id", id) })
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

    // ---- ordering ----
    //
    // Each of these takes the full list of ids in their new order and the worker
    // rewrites sort_order in one batch, exactly as the web sends it on drop.

    suspend fun reorderWallets(ids: List<Long>) = reorder("reorder_wallets", ids)

    suspend fun reorderTransactionCategories(ids: List<Long>) =
        reorder("reorder_transaction_categories", ids)

    suspend fun reorderSavingsGoals(ids: List<Long>) = reorder("reorder_savings_goals", ids)

    private suspend fun reorder(command: String, ids: List<Long>) {
        rpc.call(
            command,
            buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) },
        )
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

    /**
     * Spend a purchase goal: the apartado is released and the money leaves the
     * wallet as an expense. The server does all of it in one batch.
     */
    suspend fun useGoal(id: Long) {
        rpc.call("use_savings_goal", buildJsonObject { put("id", id) })
        cache.invalidateReads()
    }

    /**
     * Graduate a fund goal into its own wallet, moving the reserved money there
     * and closing the goal. Style is optional: without it the new wallet
     * inherits the goal's own name and colour.
     */
    suspend fun convertGoalToWallet(
        id: Long,
        name: String?,
        color: String?,
        categoryId: Long?,
    ) {
        rpc.call(
            "convert_goal_to_wallet",
            buildJsonObject {
                put("id", id)
                name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
                color?.let { put("color", it) }
                categoryId?.let { put("categoryId", it) }
            },
        )
        cache.invalidateReads()
    }

    /** Edit an apartado move from the movements history. */
    suspend fun updateGoalContribution(id: Long, amountCents: Long, occurredAt: String) {
        rpc.call(
            "update_goal_contribution",
            buildJsonObject {
                put("id", id)
                put("amountCents", amountCents)
                put("occurredAt", occurredAt)
            },
        )
        cache.invalidateReads()
    }

    suspend fun deleteGoalContribution(id: Long) {
        rpc.call("delete_goal_contribution", buildJsonObject { put("id", id) })
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
