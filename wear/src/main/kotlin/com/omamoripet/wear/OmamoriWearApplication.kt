package com.omamoripet.wear

import android.app.Application
import com.omamoripet.shared.model.BreathingSessionRecord
import com.omamoripet.shared.model.FEELING_MINUTE_MS
import com.omamoripet.wear.data.WatchStateRepository
import com.omamoripet.wear.sync.WatchSyncClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OmamoriWearApplication : Application() {
    private companion object {
        const val FEELING_CHECKPOINT_INTERVAL_MS = 240_000L
        const val FEELING_RETENTION_MS = 24 * 60 * FEELING_MINUTE_MS
        val OUTBOX_BACKOFF_MS = longArrayOf(60_000L, 120_000L, 300_000L, 900_000L)
    }

    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val syncClient: WatchSyncClient by lazy { WatchSyncClient(applicationContext) }
    lateinit var repository: WatchStateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = WatchStateRepository(applicationContext, syncClient)

        applicationScope.launch {
            repository.feelingContext.state.collectLatest { settings ->
                if (settings.enabledSince == null || settings.windows.isEmpty()) return@collectLatest
                val incomplete = settings.windows.filterNot { it.completed }
                if (incomplete.isNotEmpty()) {
                    while (isActive) {
                        val now = System.currentTimeMillis()
                        val earliestEnd = incomplete.minOf { it.endsAt }
                        val remainingToEnd = earliestEnd - now
                        if (remainingToEnd <= 0L) {
                            repository.feelingContext.updateWindows(checkpoint = true)
                            delay(5_000L)
                        } else {
                            delay(minOf(remainingToEnd, FEELING_CHECKPOINT_INTERVAL_MS))
                            repository.feelingContext.updateWindows(checkpoint = true)
                        }
                    }
                } else {
                    val earliestExpiry = settings.windows.minOf { it.recordedAt + FEELING_RETENTION_MS }
                    while (isActive) {
                        val remaining = earliestExpiry - System.currentTimeMillis()
                        if (remaining <= 0L) {
                            repository.feelingContext.updateWindows(checkpoint = false)
                            delay(5_000L)
                        } else {
                            delay(remaining + 1_000L)
                        }
                    }
                }
            }
        }

        applicationScope.launch {
            syncClient.outboxWakeup
                .onStart { emit(Unit) }
                .collectLatest {
                    runOutboxBackoffLoop()
                }
        }
    }

    private suspend fun runOutboxBackoffLoop() {
        var attempt = 0
        while (applicationScope.isActive) {
            val hasPending = try {
                syncClient.flushPendingHistory()
                syncClient.hasPendingHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            if (!hasPending) break
            val delayMs = OUTBOX_BACKOFF_MS[attempt.coerceAtMost(OUTBOX_BACKOFF_MS.lastIndex)]
            attempt++
            delay(delayMs)
        }
    }

    fun recordBreathingSession(record: BreathingSessionRecord) {
        applicationScope.launch { syncClient.sendBreathingSession(record) }
    }
}
