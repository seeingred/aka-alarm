package com.aka.alarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.aka.alarm.AlarmApp
import com.aka.alarm.MainActivity
import com.aka.alarm.R
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule
import com.aka.alarm.model.AlarmStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val store = (context.applicationContext as? AlarmApp)?.alarmStore
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, store)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_ARM) {
            val app = context.applicationContext as? AlarmApp ?: return
            val store = app.alarmStore

            if (store.phase is AlarmPhase.Idle) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    store.startAlarm()
                } else {
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(openAppIntent)
                }
            } else {
                store.cancelAlarm()
            }
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_ARM = "com.aka.alarm.widget.ACTION_TOGGLE_ARM"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, AlarmWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
            if (appWidgetIds.isEmpty()) return
            val store = (context.applicationContext as? AlarmApp)?.alarmStore
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id, store)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            store: AlarmStore?
        ) {
            val views = RemoteViews(context.packageName, R.layout.alarm_widget)

            val isArmed = store?.phase != null && store.phase !is AlarmPhase.Idle
            val timeRangeText = formatTimeRange(store)

            if (isArmed) {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_status_armed))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_status_dot_armed)
                views.setTextViewText(R.id.widget_toggle_button, context.getString(R.string.widget_action_disarm))
                views.setInt(R.id.widget_toggle_button, "setBackgroundResource", R.drawable.widget_button_armed_bg)
            } else {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.widget_status_disarmed))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_status_dot_disarmed)
                views.setTextViewText(R.id.widget_toggle_button, context.getString(R.string.widget_action_arm))
                views.setInt(R.id.widget_toggle_button, "setBackgroundResource", R.drawable.widget_button_disarmed_bg)
            }

            views.setTextViewText(R.id.widget_time_range, timeRangeText)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_center_content, openAppPendingIntent)

            val toggleIntent = Intent(context, AlarmWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_ARM
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                toggleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun formatTimeRange(store: AlarmStore?): String {
            if (store == null) {
                return "07:00 – 07:30"
            }
            val phaseWindow = store.phase.window
            if (phaseWindow != null) {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                return "${fmt.format(Date(phaseWindow.first))} – ${fmt.format(Date(phaseWindow.second))}"
            }

            val hour = store.selectedHour
            val minute = store.selectedMinute
            val (startMillis, endMillis) = AlarmSchedule.computeWakeWindow(System.currentTimeMillis(), hour, minute)
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "${fmt.format(Date(startMillis))} – ${fmt.format(Date(endMillis))}"
        }
    }
}
