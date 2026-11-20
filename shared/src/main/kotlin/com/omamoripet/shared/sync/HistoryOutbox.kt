package com.omamoripet.shared.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class PendingHistoryItem(val path: String, val payload: String)

interface HistoryOutboxStore {
    suspend fun enqueue(item: PendingHistoryItem)
    suspend fun pending(): List<PendingHistoryItem>
    suspend fun remove(path: String)
}

/** Retains a record until the transport has durably accepted it, even across process restarts. */
class HistoryOutbox(
    private val store: HistoryOutboxStore,
    private val deliveryMutex: Mutex,
    private val publish: suspend (PendingHistoryItem) -> Unit
) {
    suspend fun send(item: PendingHistoryItem) {
        store.enqueue(item)
        flush()
    }

    suspend fun flush() = deliveryMutex.withLock {
        for (item in store.pending()) {
            try {
                publish(item)
                store.remove(item.path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep this and subsequent items for the next retry.
                break
            }
        }
    }
}
