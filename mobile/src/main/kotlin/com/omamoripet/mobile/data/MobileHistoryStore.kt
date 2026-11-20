package com.omamoripet.mobile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.sync.OmamoriJson

internal data class MobileHistory(
    val sessions: List<BreathingSessionRecord>,
    val events: List<CalmEventRecord>,
    val firstBreathingDone: Boolean
)

/** Merges against disk inside one transaction, never against an uninitialized UI list. */
internal class MobileHistoryStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        val SESSIONS = stringPreferencesKey("breath_sessions_json")
        val EVENTS = stringPreferencesKey("calm_events_json")
        val FIRST_DONE = booleanPreferencesKey("first_breathing_completed")

        fun read(prefs: Preferences): MobileHistory {
            val sessions = OmamoriJson.decodeOrNull<List<BreathingSessionRecord>>(prefs[SESSIONS]) ?: emptyList()
            val events = OmamoriJson.decodeOrNull<List<CalmEventRecord>>(prefs[EVENTS]) ?: emptyList()
            return MobileHistory(sessions, events, prefs[FIRST_DONE] == true || sessions.any { !it.isSimulated })
        }
    }

    suspend fun recordSession(record: BreathingSessionRecord): MobileHistory {
        val prefs = dataStore.edit { prefs ->
            val sessions = (listOf(record) + read(prefs).sessions)
                .distinctBy { it.id }.sortedByDescending { it.startedAtEpochMs }.take(50)
            prefs[SESSIONS] = OmamoriJson.encode(sessions)
            if (!record.isSimulated) prefs[FIRST_DONE] = true
        }
        return read(prefs)
    }

    suspend fun recordEvent(record: CalmEventRecord): MobileHistory {
        val prefs = dataStore.edit { prefs ->
            val events = (listOf(record) + read(prefs).events)
                .distinctBy { it.id }.sortedByDescending { it.timestampEpochMs }.take(50)
            prefs[EVENTS] = OmamoriJson.encode(events)
        }
        return read(prefs)
    }

    suspend fun clearSimulated(): MobileHistory {
        val prefs = dataStore.edit { prefs ->
            val history = read(prefs)
            prefs[SESSIONS] = OmamoriJson.encode(history.sessions.filterNot { it.isSimulated })
            prefs[EVENTS] = OmamoriJson.encode(history.events.filterNot { it.isSimulated })
        }
        return read(prefs)
    }
}
