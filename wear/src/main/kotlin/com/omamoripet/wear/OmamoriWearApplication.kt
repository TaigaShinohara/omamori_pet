package com.omamoripet.wear

import android.app.Application
import com.omamoripet.wear.data.WatchStateRepository
import com.omamoripet.wear.sync.WatchSyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.omamoripet.shared.model.BreathingSessionRecord

class OmamoriWearApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: WatchStateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = WatchStateRepository(applicationContext)
        applicationScope.launch {
            val syncClient = WatchSyncClient(applicationContext)
            while (isActive) {
                try {
                    syncClient.flushPendingHistory()
                    repository.feelingContext.pruneExpired(System.currentTimeMillis())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Retry a temporarily unavailable local store on the next pass.
                }
                delay(60_000L)
            }
        }
    }

    fun recordBreathingSession(record: BreathingSessionRecord) {
        applicationScope.launch { WatchSyncClient(applicationContext).sendBreathingSession(record) }
    }
}
