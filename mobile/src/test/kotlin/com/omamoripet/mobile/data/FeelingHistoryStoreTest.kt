package com.omamoripet.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omamoripet.shared.model.Feeling
import com.omamoripet.shared.model.FEELING_MINUTE_COUNT
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.model.FeelingMinute
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

    @Test
    fun `watch data attaches only to its matching record and can be removed separately`() = runBlocking {
        val store = FeelingHistoryStore(dataStore)
        val first = FeelingRecord("one", Feeling.ANXIOUS, 1_000L)
        val second = FeelingRecord("two", Feeling.LOW, 2_000L)
        val context = FeelingContext(
            first.recordedAtEpochMs, null, null,
            List(FEELING_MINUTE_COUNT) { index ->
                if (index == 0) FeelingMinute(medianBpm = 78, sampleCount = 2) else FeelingMinute()
            }
        )
        store.record(first)
        store.record(second)
        store.updateContext(second.id, context)
        assertEquals(listOf(second, first), store.records.first())

        store.updateContext(first.id, context)
        store.updateContext(first.id, context)
        assertEquals(listOf(second, first.copy(context = context)), FeelingHistoryStore(dataStore).records.first())

        store.updateContext(first.id, null)
        assertEquals(listOf(second, first), store.records.first())
    }

    @Test
    fun `late health and pressure observations merge into the right record independently`() = runBlocking {
        val store = FeelingHistoryStore(dataStore)
        val older = FeelingRecord("older", Feeling.LOW, 1_000)
        val newer = FeelingRecord("newer", Feeling.ANXIOUS, 2_000, recordedZoneId = "Asia/Tokyo")
        store.record(older)
        store.record(newer)
        val sleep = FeelingSleep(FeelingComparison(360, 420, 8), 1_500)
        val pulse = FeelingComparison(74, 69, 4, 8)
        val pressure = FeelingPressure(1008.1, 2_001)
        store.updateHealth(newer.id, FeelingHealth(sleep = sleep))
        val pressureUpdate = launch { store.updatePressure(newer.id, pressure) }
        val pulseUpdate = launch { store.updateHealth(newer.id, FeelingHealth(heart5 = pulse)) }
        pressureUpdate.join()
        pulseUpdate.join()
        val updated = newer.copy(health = FeelingHealth(sleep = sleep, heart5 = pulse), pressure = pressure)
        assertEquals(listOf(updated, older), FeelingHistoryStore(dataStore).records.first())
    }
}
