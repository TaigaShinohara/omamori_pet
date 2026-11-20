package com.omamoripet.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventAction
import com.omamoripet.shared.model.CalmEventRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MobileHistoryStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val job = SupervisorJob()
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
            File(temporaryFolder.root, "history.preferences_pb")
        }
    }

    private fun session(id: String, time: Long, simulated: Boolean = false) =
        BreathingSessionRecord(id, time, 60, null, null, false, simulated)

    @After fun closeStore() = runBlocking { job.cancelAndJoin() }

    @Test
    fun `cold receiver merges into stored history without loading a UI first`() = runBlocking {
        MobileHistoryStore(dataStore).recordSession(session("old", 1L))
        val coldReceiver = MobileHistoryStore(dataStore)
        val result = coldReceiver.recordSession(session("new", 2L))
        assertEquals(listOf("new", "old"), result.sessions.map { it.id })
        assertEquals(result, MobileHistoryStore.read(dataStore.data.first()))
    }

    @Test
    fun `concurrent and replayed deliveries do not lose or duplicate records`() = runBlocking {
        val store = MobileHistoryStore(dataStore)
        (1..20).map { i -> launch(Dispatchers.Default) { store.recordSession(session("$i", i.toLong())) } }
            .forEach { it.join() }
        val result = store.recordSession(session("20", 20L))
        assertEquals(20, result.sessions.size)
        assertEquals((20 downTo 1).map { "$it" }, result.sessions.map { it.id })
        assertTrue(result.firstBreathingDone)
    }

    @Test
    fun `late old delivery cannot evict the newest fifty records`() = runBlocking {
        val store = MobileHistoryStore(dataStore)
        for (i in 1..55) store.recordSession(session("$i", i.toLong()))
        val result = store.recordSession(session("very-old", 0L))
        assertEquals(50, result.sessions.size)
        assertEquals("55", result.sessions.first().id)
        assertEquals("6", result.sessions.last().id)
    }

    @Test
    fun `events merge across cold receivers and deduplicate acknowledgements`() = runBlocking {
        val record = CalmEventRecord("one", 1L, 90, 65, 100, CalmEventAction.ALERT_SENT)
        MobileHistoryStore(dataStore).recordEvent(record)
        val result = MobileHistoryStore(dataStore).recordEvent(record.copy(id = "two", timestampEpochMs = 2L))
        assertEquals(listOf("two", "one"), result.events.map { it.id })
        assertEquals(2, MobileHistoryStore(dataStore).recordEvent(record).events.size)
    }

    @Test
    fun `clearing demo history preserves real records and first completion`() = runBlocking {
        val store = MobileHistoryStore(dataStore)
        store.recordSession(session("real", 1L))
        store.recordSession(session("demo", 2L, true))
        val result = store.clearSimulated()
        assertEquals(listOf("real"), result.sessions.map { it.id })
        assertTrue(result.firstBreathingDone)
    }
}
