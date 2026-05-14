package com.aka.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmStore
import com.aka.alarm.ui.AkaAlarmTheme
import com.aka.alarm.ui.AlarmScreen
import com.aka.alarm.ui.MainScreen
import kotlinx.coroutines.flow.MutableSharedFlow

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
                    val phaseKind = store.phase.kind
                    when (phaseKind) {
                        AlarmPhase.Kind.ALARMING,
                        AlarmPhase.Kind.SNOOZING -> AlarmScreen(store)
                        else -> MainScreen(store, onStart = ::tryStartAlarm)
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
}
