package com.aka.alarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmStore
import com.aka.alarm.ui.AkaAlarmTheme
import com.aka.alarm.ui.AlarmScreen
import com.aka.alarm.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            store.startAlarm()
        } else {
            store.micPermissionDenied = true
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The service runs whether or not this is granted; the notification just
            won't show. We still ask for parity with iOS UX. */ }

    private lateinit var store: AlarmStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = (application as AlarmApp).alarmStore

        // Ask for POST_NOTIFICATIONS up-front on Android 13+ so the foreground
        // service notification will be visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AkaAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    // Keep the gradient full-bleed but lift interactive content
                    // above the navigation bar: on API 26–28 the 3-button navbar
                    // is opaque and was covering the Start button / slide hints.
                    // On gesture-nav devices this adds only the small inset.
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        val phaseKind = store.phase.kind
                        when (phaseKind) {
                            AlarmPhase.Kind.ALARMING,
                            AlarmPhase.Kind.SNOOZING -> AlarmScreen(store)
                            else -> MainScreen(store, onStart = ::tryStartAlarm)
                        }
                    }
                    if (store.micPermissionDenied) {
                        AlertDialog(
                            onDismissRequest = { store.micPermissionDenied = false },
                            title = { Text("Microphone access required") },
                            text = {
                                Text(
                                    "aka Alarm needs the microphone to detect when you start " +
                                        "stirring. Enable it in Settings → Apps → aka Alarm."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { store.micPermissionDenied = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun tryStartAlarm() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> store.startAlarm()
            else -> requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ----- Debug-only broadcast receiver for screenshot capture ----------
    // Forces phase transitions via ADB:
    //   adb shell am broadcast -a com.aka.alarm.DEBUG -e action fire
    //   adb shell am broadcast -a com.aka.alarm.DEBUG -e action snooze
    //   adb shell am broadcast -a com.aka.alarm.DEBUG -e action cancel
    // Harmless to ship — the broadcast action is private and only registered
    // while the activity is in the foreground.
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("action")) {
                "fire" -> store.debugForceAlarming()
                "snooze" -> store.debugForceSnoozing()
                "cancel" -> store.cancelAlarm()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-heal: if the baseline wakeup was dropped while we slept (OEM alarm
        // throttling, revoked exact-alarm permission), catch up now that the user
        // is looking at the app.
        store.recheckPhase()
        ContextCompat.registerReceiver(
            this,
            debugReceiver,
            IntentFilter("com.aka.alarm.DEBUG"),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(debugReceiver) } catch (_: IllegalArgumentException) {}
    }
}
