package com.omamoripet.wear.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.sync.DataLayerPaths
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PhoneBaselinePayload
import com.omamoripet.shared.sync.PreferencesSyncPayload
import com.omamoripet.shared.sync.WatchLivePayload
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import kotlinx.coroutines.tasks.await

class WatchSyncClient(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun pushLiveState(payload: WatchLivePayload, urgent: Boolean = true) {
        runCatching {
            val request = PutDataMapRequest.create(DataLayerPaths.WATCH_LIVE_STATE).apply {
                dataMap.putString(DataLayerPaths.KEY_PAYLOAD, OmamoriJson.encode(payload))
            }.asPutDataRequest().apply {
                if (urgent) setUrgent()
            }
            dataClient.putDataItem(request).await()
        }
    }

    suspend fun pushPreferences(payload: PreferencesSyncPayload) {
        runCatching {
            val request = PutDataMapRequest.create(DataLayerPaths.ALERT_PREFERENCES).apply {
                dataMap.putString(DataLayerPaths.KEY_PAYLOAD, OmamoriJson.encode(payload))
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    suspend fun sendBreathingSession(record: BreathingSessionRecord) {
        sendMessageToAllNodes(DataLayerPaths.BREATHING_SESSION, OmamoriJson.encode(record))
    }

    suspend fun sendCalmEvent(record: CalmEventRecord) {
        sendMessageToAllNodes(DataLayerPaths.CALM_EVENT, OmamoriJson.encode(record))
    }

    private suspend fun sendMessageToAllNodes(path: String, json: String) {
        runCatching {
            val nodes = nodeClient.connectedNodes.await()
            val bytes = json.toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, bytes).await()
            }
        }
    }
}

class WatchDataSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repo = (application as OmamoriWearApplication).repository
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(DataLayerPaths.KEY_PAYLOAD)
                when (path) {
                    DataLayerPaths.PHONE_BASELINE_AND_ADVICE -> {
                        OmamoriJson.decodeOrNull<PhoneBaselinePayload>(json)?.let {
                            repo.applyPhoneBaselineAndAdvice(it)
                        }
                    }
                    DataLayerPaths.ALERT_PREFERENCES -> {
                        OmamoriJson.decodeOrNull<PreferencesSyncPayload>(json)?.let {
                            repo.updatePreferences(it.preferences, syncToPhone = false)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == DataLayerPaths.COMMAND_START_BREATHING) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_BREATHING, true)
            }
            startActivity(intent)
        }
    }
}
