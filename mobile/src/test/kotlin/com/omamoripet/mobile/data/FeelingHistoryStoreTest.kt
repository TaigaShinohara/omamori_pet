package com.omamoripet.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.sync.OmamoriJson
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
    fun `existing records still decode when the feeling type is shared with Wear OS`() {
        val old = """{"id":"one","feeling":"ANXIOUS","recordedAtEpochMs":1000}"""
        val entry = FeelingRecord("one", Feeling.ANXIOUS, 1_000L)
        assertEquals(entry, OmamoriJson.decodeOrNull<FeelingRecord>(old))
        assertEquals(old, OmamoriJson.encode(entry))
    }
}
