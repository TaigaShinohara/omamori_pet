package com.omamoripet.mobile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private fun read(prefs: Preferences): List<FeelingRecord> {
        val saved = prefs[RECORDS] ?: return emptyList()
        return OmamoriJson.decodeOrNull<List<FeelingRecord>>(saved) ?: throw IOException("Invalid feeling records")
    }

    private companion object {
        val RECORDS = stringPreferencesKey("feeling_records")
    }
}
