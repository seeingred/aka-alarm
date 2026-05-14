package com.aka.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aka.alarm.AlarmApp
import com.aka.alarm.MainActivity
import com.aka.alarm.R
import com.aka.alarm.model.AlarmPhase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Foreground service that keeps the process (and microphone) alive while an
 * alarm is armed. Android does not allow continuous mic access from a backgrounded
 * regular app — a foreground service with `microphone` type is the supported
 * pattern. The service itself does no work; [AlarmStore] handles all logic.
 * The service just owns the persistent notification and is started/stopped by
 * the store on phase transitions.
 */
class AlarmService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val store = (application as AlarmApp).alarmStore

        lifecycleScope.launch {
            snapshotFlow { store.phase }
                .distinctUntilChanged()
                .collect { phase ->
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(phase))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val store = (application as AlarmApp).alarmStore
        startForeground(
            NOTIFICATION_ID,
            buildNotification(store.phase),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(phase: AlarmPhase): Notification {
        val (title, body) = phase.notificationCopy()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun AlarmPhase.notificationCopy(): Pair<String, String> {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return when (val p = this) {
            AlarmPhase.Idle -> "aka Alarm" to "Idle"
            is AlarmPhase.Monitoring -> "aka Alarm is listening…" to
                "Wake window: ${fmt.format(p.start)} – ${fmt.format(p.end)}"
            is AlarmPhase.InWindow -> "aka Alarm is listening…" to
                "Wake window active: ${fmt.format(p.start)} – ${fmt.format(p.end)}"
            is AlarmPhase.Alarming -> "Wake up" to "Move the phone to snooze"
            is AlarmPhase.Snoozing -> "Snoozing" to
                "Until ${fmt.format(p.until)}"
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a wake-up alarm is armed"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "alarm-status"
        const val NOTIFICATION_ID = 1
    }
}
