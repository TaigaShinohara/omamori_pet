package com.omamoripet.mobile.sync

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.omamoripet.mobile.OmamoriMobileApplication
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.CalmEventRecord
import com.omamoripet.shared.sync.DataLayerPaths
import com.omamoripet.shared.sync.OmamoriJson
import com.omamoripet.shared.sync.PhoneBaselinePayload
import com.omamoripet.shared.sync.PreferencesSyncPayload
import com.omamoripet.shared.sync.WatchLivePayload
import kotlinx.coroutines.tasks.await

class PhoneWearSyncClient(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun pushBaselineAndAdvice(payload: PhoneBaselinePayload) {
        runCatching {
            val request = PutDataMapRequest.create(DataLayerPaths.PHONE_BASELINE_AND_ADVICE).apply {
                dataMap.putString(DataLayerPaths.KEY_PAYLOAD, OmamoriJson.encode(payload))
            }.asPutDataRequest().setUrgent()
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

    suspend fun sendStartBreathingToWatch(): Boolean {
        return runCatching {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) return false
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, DataLayerPaths.COMMAND_START_BREATHING, ByteArray(0)).await()
            }
            true
        }.getOrDefault(false)
    }
}

class PhoneWearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repo = (application as OmamoriMobileApplication).repository
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == DataLayerPaths.WATCH_LIVE_STATE
            ) {
                val raw = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(DataLayerPaths.KEY_PAYLOAD)
                OmamoriJson.decodeOrNull<WatchLivePayload>(raw)?.let {
                    repo.applyWatchLivePayload(it)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val repo = (application as OmamoriMobileApplication).repository
        val raw = messageEvent.data.toString(Charsets.UTF_8)
        when (messageEvent.path) {
            DataLayerPaths.BREATHING_SESSION -> {
                OmamoriJson.decodeOrNull<BreathingSessionRecord>(raw)?.let {
                    repo.recordBreathingSession(it)
                }
            }
            DataLayerPaths.CALM_EVENT -> {
                OmamoriJson.decodeOrNull<CalmEventRecord>(raw)?.let {
                    repo.recordCalmEvent(it)
                }
            }
        }
    }
}
