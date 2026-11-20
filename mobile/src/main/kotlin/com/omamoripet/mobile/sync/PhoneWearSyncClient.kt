package com.omamoripet.mobile.sync

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataItem
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class PhoneWearSyncClient(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun pushBaselineAndAdvice(payload: PhoneBaselinePayload): Boolean =
        runCatching {
            val request = PutDataMapRequest.create(DataLayerPaths.PHONE_BASELINE_AND_ADVICE).apply {
                dataMap.putString(DataLayerPaths.KEY_PAYLOAD, OmamoriJson.encode(payload))
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            true
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)

    suspend fun receiveHistory(
        item: DataItem,
        onSession: suspend (BreathingSessionRecord) -> Unit,
        onEvent: suspend (CalmEventRecord) -> Unit
    ) {
        val path = item.uri.path ?: return
        if (!path.startsWith("${DataLayerPaths.BREATHING_SESSION}/") &&
            !path.startsWith("${DataLayerPaths.CALM_EVENT}/")) return
        val raw = DataMapItem.fromDataItem(item).dataMap.getString(DataLayerPaths.KEY_PAYLOAD)
        when {
            path.startsWith("${DataLayerPaths.BREATHING_SESSION}/") -> {
                val record = OmamoriJson.decodeOrNull<BreathingSessionRecord>(raw) ?: return
                onSession(record)
            }
            else -> {
                val record = OmamoriJson.decodeOrNull<CalmEventRecord>(raw) ?: return
                onEvent(record)
            }
        }
        // Acknowledge only after the phone has committed the record to disk.
        runCatching { dataClient.deleteDataItems(item.uri).await() }
            .onFailure { if (it is CancellationException) throw it }
    }

    suspend fun drainHistory(
        onSession: suspend (BreathingSessionRecord) -> Unit,
        onEvent: suspend (CalmEventRecord) -> Unit
    ) {
        try {
            val items = dataClient.dataItems.await()
            try {
                items.forEach { receiveHistory(it, onSession, onEvent) }
            } finally {
                items.release()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Durable DataItems remain available for the next refresh.
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

    suspend fun hasConnectedWatch(): Boolean {
        return runCatching {
            nodeClient.connectedNodes.await().isNotEmpty()
        }.getOrDefault(false)
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

    suspend fun sendExitDemoToWatch() {
        runCatching {
            val nodes = nodeClient.connectedNodes.await()
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, DataLayerPaths.COMMAND_EXIT_DEMO, ByteArray(0)).await()
            }
        }
    }
}

class PhoneWearSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) = runBlocking {
        val repo = (application as OmamoriMobileApplication).repository
        repo.awaitInitialized()
        val syncClient = PhoneWearSyncClient(applicationContext)
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                val raw = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(DataLayerPaths.KEY_PAYLOAD)
                syncClient.receiveHistory(event.dataItem, repo::recordBreathingSession, repo::recordCalmEvent)
                when (path) {
                    DataLayerPaths.WATCH_LIVE_STATE -> {
                        OmamoriJson.decodeOrNull<WatchLivePayload>(raw)?.let {
                            repo.applyWatchLivePayload(it)
                        }
                    }
                    DataLayerPaths.ALERT_PREFERENCES -> {
                        OmamoriJson.decodeOrNull<PreferencesSyncPayload>(raw)?.let {
                            repo.updatePreferences(it.preferences, syncToWatch = false)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) = runBlocking {
        val repo = (application as OmamoriMobileApplication).repository
        repo.awaitInitialized()
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
