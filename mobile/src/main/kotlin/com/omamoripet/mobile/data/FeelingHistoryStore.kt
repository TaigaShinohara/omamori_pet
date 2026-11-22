package com.omamoripet.mobile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omamoripet.shared.model.FeelingContext
import com.omamoripet.shared.sync.OmamoriJson
import java.io.IOException
import kotlinx.coroutines.flow.map

internal class FeelingHistoryStore(private val dataStore: DataStore<Preferences>) {
    val records = dataStore.data.map(::read)

    suspend fun record(entry: FeelingRecord): List<FeelingRecord> {
        val updated = dataStore.edit { prefs ->
            val records = (listOf(entry) + read(prefs)).distinctBy { it.id }.sortedByDescending { it.recordedAtEpochMs }
            prefs[RECORDS] = OmamoriJson.encode(records)
        }
        return read(updated)
    }

    suspend fun updateContext(id: String, context: FeelingContext?): List<FeelingRecord> {
        return updateRecord(id) { target ->
            if (context == null || context.recordedAtEpochMs == target.recordedAtEpochMs) {
                target.copy(context = context)
            } else {
                target
            }
        }
    }

    suspend fun updateHealth(id: String, health: FeelingHealth): List<FeelingRecord> =
        updateRecord(id) { it.copy(health = it.health?.withUpdates(health) ?: health) }

    suspend fun updatePressure(id: String, pressure: FeelingPressure): List<FeelingRecord> =
        updateRecord(id) { it.copy(pressure = pressure) }

    private suspend fun updateRecord(id: String, update: (FeelingRecord) -> FeelingRecord): List<FeelingRecord> {
        val updated = dataStore.edit { prefs ->
            val records = read(prefs)
            val target = records.firstOrNull { it.id == id } ?: return@edit
            val updatedRecord = update(target)
            if (target == updatedRecord) return@edit
            prefs[RECORDS] = OmamoriJson.encode(records.map {
                if (it.id == id) updatedRecord else it
            })
        }
        return read(updated)
    }

    private fun read(prefs: Preferences): List<FeelingRecord> {
        val saved = prefs[RECORDS] ?: return emptyList()
        return OmamoriJson.decodeOrNull<List<FeelingRecord>>(saved) ?: throw IOException("Invalid feeling records")
    }

    private companion object {
        val RECORDS = stringPreferencesKey("feeling_records")
    }
}
