package com.omamoripet.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeelingHistoryStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val job = SupervisorJob()
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(temporaryFolder.root, "feelings.preferences_pb")
        }
    }

    @After fun closeStore() = runBlocking { job.cancelAndJoin() }

    @Test
    fun `records are durably saved in time order without losing concurrent entries`() = runBlocking {
        val store = FeelingHistoryStore(dataStore)
        assertEquals(emptyList<FeelingRecord>(), store.records.first())
        val entries = Feeling.entries.mapIndexed { index, feeling ->
            FeelingRecord("$index", feeling, 1000L + index)
        }
        entries.map { entry -> launch(Dispatchers.Default) { store.record(entry) } }.forEach { it.join() }
        store.record(entries.last())
        assertEquals(entries.reversed(), FeelingHistoryStore(dataStore).records.first())
    }

    @Test
    fun `a saved face expires at three minutes even when reopened`() {
        val entry = FeelingRecord("one", Feeling.ANXIOUS, 1000L)
        assertEquals(0L, entry.faceRemainingMs(999L))
        assertEquals(180_000L, entry.faceRemainingMs(1000L))
        assertEquals(1L, entry.faceRemainingMs(180_999L))
        assertEquals(0L, entry.faceRemainingMs(181_000L))
    }
}
