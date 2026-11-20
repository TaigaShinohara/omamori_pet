package com.omamoripet.shared.sync

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test

class HistoryOutboxTest {
    private class Store : HistoryOutboxStore {
        val items = linkedMapOf<String, PendingHistoryItem>()
        override suspend fun enqueue(item: PendingHistoryItem) { items[item.path] = item }
        override suspend fun pending() = items.values.toList()
        override suspend fun remove(path: String) { items.remove(path) }
    }

    @Test
    fun `failed delivery is retried after sender recreation`() = runBlocking {
        val store = Store()
        val item = PendingHistoryItem("/breathing/one", "record")
        HistoryOutbox(store, Mutex()) { throw IOException("offline") }.send(item)
        assertEquals(listOf(item), store.pending())
        val delivered = mutableListOf<PendingHistoryItem>()
        HistoryOutbox(store, Mutex()) { delivered += it }.flush()
        assertEquals(listOf(item), delivered)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `concurrent senders preserve distinct records during an outage`() = runBlocking {
        val store = Store()
        val mutex = Mutex()
        val sender = HistoryOutbox(store, mutex) { throw IOException("offline") }
        val jobs = (1..20).map { i -> launch { sender.send(PendingHistoryItem("/event/$i", "$i")) } }
        jobs.forEach { it.join() }
        assertEquals(20, store.pending().size)
        val delivered = mutableListOf<String>()
        HistoryOutbox(store, mutex) { delivered += it.path }.flush()
        assertEquals(20, delivered.distinct().size)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `cancellation keeps a record until durable publication succeeds`() = runBlocking {
        val store = Store()
        val item = PendingHistoryItem("/breathing/one", "record")
        try {
            HistoryOutbox(store, Mutex()) { throw CancellationException("stopped") }.send(item)
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(item), store.pending())
        }
    }
}
