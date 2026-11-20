package com.omamoripet.wear.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omamoripet.shared.sync.HistoryOutboxStore
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PendingHistoryItem
import kotlinx.coroutines.flow.first

internal val Context.historyOutboxDataStore by preferencesDataStore(name = "omamori_history_outbox")

internal class WatchHistoryOutboxStore(private val dataStore: DataStore<Preferences>) : HistoryOutboxStore {
    private val key = stringPreferencesKey("pending_history")

    override suspend fun enqueue(item: PendingHistoryItem) {
        dataStore.edit { prefs ->
            val items = decode(prefs).filterNot { it.path == item.path } + item
            prefs[key] = OmamoriJson.encode(items)
        }
    }

    override suspend fun pending(): List<PendingHistoryItem> = decode(dataStore.data.first())

    override suspend fun remove(path: String) {
        dataStore.edit { prefs ->
            prefs[key] = OmamoriJson.encode(decode(prefs).filterNot { it.path == path })
        }
    }

    private fun decode(prefs: Preferences): List<PendingHistoryItem> =
        OmamoriJson.decodeOrNull<List<PendingHistoryItem>>(prefs[key]) ?: emptyList()
}
