package com.asura.finanzas.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

/**
 * Offline capture queue, deliberately append-only: only the three capture
 * commands can be queued, so replaying one can never conflict with an edit made
 * elsewhere. Each item carries the `clientId` the server uses for idempotency
 * (unique index on `transactions.client_id`), so a replay after a dropped
 * response cannot insert twice.
 *
 * The queue is local truth about what has not been sent yet; the server stays
 * the source of truth for balances, which is why pending items are listed on
 * their own and never folded into a total.
 *
 * Mirrors `src/lib/outbox.ts`.
 */
@Serializable
data class OutboxItem(
    /** Also sent as `clientId`, which is what makes a retry safe. */
    val id: String,
    /** One of add_income | add_expense | add_transfer. */
    val command: String,
    val args: JsonObject,
    val createdAt: String,
    /** "pending" until it syncs; "error" when the server refused it. */
    val status: String = "pending",
    val errorMsg: String? = null,
)

class Outbox(context: Context, private val rpc: RpcClient) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "outbox_v1.json")
    private val mutex = Mutex()

    private val _items = MutableStateFlow(load())

    /** What is still waiting, newest last. */
    val items: StateFlow<List<OutboxItem>> = _items

    private fun load(): List<OutboxItem> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(OutboxItem.serializer()), file.readText())
    }.getOrDefault(emptyList())

    private fun persist(next: List<OutboxItem>) {
        _items.value = next
        runCatching {
            file.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(OutboxItem.serializer()),
                    next,
                ),
            )
        }
    }

    /**
     * Send the command, and queue it only when the request never reached the
     * server. A refusal (validation, 401) is rethrown so the form can show it:
     * queueing something the server already rejected would just fail forever.
     */
    suspend fun submitOrQueue(command: String, args: JsonObject): Boolean {
        val clientId = UUID.randomUUID().toString()
        val full = JsonObject(args + ("clientId" to kotlinx.serialization.json.JsonPrimitive(clientId)))
        try {
            rpc.call(command, full)
            return false
        } catch (_: NetworkException) {
            // No signal: fall through and queue it.
        }
        mutex.withLock {
            persist(
                _items.value + OutboxItem(
                    id = clientId,
                    command = command,
                    args = full,
                    createdAt = java.time.Instant.now().toString(),
                ),
            )
        }
        return true
    }

    /**
     * Send pending items oldest first. A network failure stops the run — the
     * rest stay queued for the next attempt — while a server refusal marks that
     * one item for manual review and lets the others through.
     *
     * Returns how many items synced.
     */
    suspend fun flush(): Int = mutex.withLock {
        var synced = 0
        for (item in _items.value.filter { it.status == "pending" }) {
            try {
                rpc.call(item.command, item.args)
                synced++
                persist(_items.value.filterNot { it.id == item.id })
            } catch (_: NetworkException) {
                break
            } catch (e: Exception) {
                persist(
                    _items.value.map {
                        if (it.id == item.id) {
                            it.copy(status = "error", errorMsg = e.message)
                        } else {
                            it
                        }
                    },
                )
            }
        }
        synced
    }

    suspend fun discard(id: String) = mutex.withLock {
        persist(_items.value.filterNot { it.id == id })
    }

    suspend fun retry(id: String) = mutex.withLock {
        persist(
            _items.value.map {
                if (it.id == id) it.copy(status = "pending", errorMsg = null) else it
            },
        )
    }

    /** Dropped on sign-out with the rest of the local state. */
    fun clear() {
        persist(emptyList())
    }
}
