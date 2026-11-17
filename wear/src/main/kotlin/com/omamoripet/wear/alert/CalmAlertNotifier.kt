package com.omamoripet.wear.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omamoripet.wear.MainActivity
import com.omamoripet.wear.OmamoriWearApplication
import com.omamoripet.wear.R

object CalmAlertNotifier {
    const val CHANNEL_MONITOR = "omamori_monitor"
    const val CHANNEL_ALERT = "omamori_calm_alert"
    const val NOTIF_ID_MONITOR = 1001
    const val NOTIF_ID_ALERT = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.notif_monitor_channel),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.notif_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(false) // 驚かせない専用ソフト振動を自前制御する
        }
        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(alertChannel)
    }

    fun notifyGentleElevation(context: Context, currentBpm: Int, deltaBpm: Int) {
        ensureChannels(context)
        vibrateSoftTwoBeats(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val breatheIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_BREATHING, true)
            putExtra(MainActivity.EXTRA_TRIGGERED_BY_ALERT, true)
        }
        val breathePending = PendingIntent.getActivity(
            context,
            201,
            breatheIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_SNOOZE_1H
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            202,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.pet_elevated)
            .setContentTitle(context.getString(R.string.notif_alert_title))
            .setContentText(context.getString(R.string.notif_alert_body, currentBpm, deltaBpm))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(breathePending)
            .setAutoCancel(true)
            .addAction(
                R.drawable.pet_breathing,
                context.getString(R.string.action_breathe_1m),
                breathePending
            )
            .addAction(
                R.drawable.pet_calm,
                context.getString(R.string.action_snooze_1h),
                snoozePending
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_ALERT, notification)
    }

    /**
     * 不安な時に驚かせないよう、緩やかな 60bpm テンポで 2 回だけ控えめに振動する。
     */
    private fun vibrateSoftTwoBeats(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val timings = longArrayOf(0L, 140L, 860L, 180L)
        val amplitudes = intArrayOf(0, 75, 0, 95)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}

class AlertActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE_1H = "com.omamoripet.wear.ACTION_SNOOZE_1H"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SNOOZE_1H) {
            val app = context.applicationContext as? OmamoriWearApplication
            app?.repository?.snoozeAlertsForMinutes(60)
            NotificationManagerCompat.from(context).cancel(CalmAlertNotifier.NOTIF_ID_ALERT)
        }
    }
}
