package com.omamoripet.wear.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omamoripet.shared.sync.HistoryOutbox
import com.omamoripet.shared.sync.PendingHistoryItem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchHistoryOutboxStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val jobs = mutableListOf<Job>()

    private fun openStore(): Pair<Job, WatchHistoryOutboxStore> {
        val job = SupervisorJob().also { jobs += it }
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(temporaryFolder.root, "outbox.preferences_pb")
        }
        return job to WatchHistoryOutboxStore(dataStore)
    }

    @After fun closeStores() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    @Test
    fun `failed publication survives process restart and is then drained`() = runBlocking {
        val (job, store) = openStore()
        val item = PendingHistoryItem("/omamori/breathing_session/one", "record")
        HistoryOutbox(store, Mutex()) { throw IOException("temporarily unavailable") }.send(item)
        job.cancelAndJoin()
        val (_, restored) = openStore()
        assertEquals(listOf(item), restored.pending())
        val delivered = mutableListOf<PendingHistoryItem>()
        HistoryOutbox(restored, Mutex()) { delivered += it }.flush()
        assertEquals(listOf(item), delivered)
        assertTrue(restored.pending().isEmpty())
    }
}
