package com.aka.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aka.alarm.Tuning
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainScreen(store: AlarmStore, onStart: () -> Unit) {
    when (store.phase.kind) {
        AlarmPhase.Kind.IDLE -> SetAlarmView(store, onStart)
        AlarmPhase.Kind.MONITORING,
        AlarmPhase.Kind.IN_WINDOW -> MonitoringView(store)
        else -> Box(Modifier.fillMaxSize())
    }
}

// MARK: - Set Alarm

@Composable
private fun SetAlarmView(store: AlarmStore, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "Wake-up window",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            NumberWheel(
                values = (0..23).toList(),
                selection = store.selectedHour,
                onSelectionChange = { store.selectedHour = it },
                modifier = Modifier.weight(1f),
            )
            Text(
                ":",
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            NumberWheel(
                values = listOf(0, 15, 30, 45),
                selection = store.selectedMinute,
                onSelectionChange = { store.selectedMinute = it },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))

        Text(
            text = formatWindowLabel(store.selectedHour, store.selectedMinute),
            fontSize = 56.sp,
            fontWeight = FontWeight.Thin,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStart,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp)
        ) {
            Text("Start", fontSize = 18.sp)
        }
    }
}

private fun formatWindowLabel(hour: Int, minute: Int): String {
    val totalStart = hour * 60 + minute
    val totalEnd = totalStart + Tuning.wakeWindowDuration.inWholeMinutes.toInt()
    val sh = (totalStart / 60) % 24; val sm = totalStart % 60
    val eh = (totalEnd / 60) % 24; val em = totalEnd % 60
    return "%02d:%02d – %02d:%02d".format(sh, sm, eh, em)
}

// MARK: - Monitoring

@Composable
private fun MonitoringView(store: AlarmStore) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(0f) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -with(density) { 120.dp.toPx() }) {
                            store.cancelAlarm()
                        }
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dy ->
                        val next = dragOffset + dy
                        if (next < 0f) dragOffset = next
                    }
                )
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        val tFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        Text(
            tFmt.format(Date(nowMillis)),
            fontSize = 64.sp,
            fontWeight = FontWeight.Thin,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        store.phase.window?.let { (start, end) ->
            val wFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            Text(
                "${wFmt.format(Date(start))} – ${wFmt.format(Date(end))}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(24.dp))

        MicLevelBar(
            currentDb = store.micLevelDb,
            baselineDb = store.baselineDb,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(80.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = if (store.phase.kind == AlarmPhase.Kind.IN_WINDOW)
                "Listening for stirring…"
            else "Learning room baseline…",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.weight(1f))
        SlideUpHint(label = "Slide up to cancel")
    }
}

// MARK: - Mic level

@Composable
private fun MicLevelBar(
    currentDb: Double,
    baselineDb: Double,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val curFrac = normalize(currentDb).toFloat()
        val baselineFrac = normalize(baselineDb).toFloat()

        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
        // Level
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(curFrac)
                .background(
                    color = Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
        // Baseline marker
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .offset { IntOffset((maxWidthPx * baselineFrac).roundToInt(), 0) }
                .background(Color(0xFFFFA500))
        )
    }
}

private fun normalize(db: Double): Double {
    // Display uses `DISPLAY_DB_FLOOR` (tighter than `DB_FLOOR`) so subtle ambient
    // and movement levels visibly fill the bar at night. The detector itself
    // still works against the full `DB_FLOOR`.
    val floor = Tuning.DISPLAY_DB_FLOOR
    val clamped = db.coerceIn(floor, 0.0)
    return (clamped - floor) / -floor
}

// MARK: - Slide hint

@Composable
fun SlideUpHint(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⌃", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
    }
}
