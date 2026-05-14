package com.aka.alarm.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AlarmScreen(store: AlarmStore) {
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

        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        Text(
            fmt.format(Date(nowMillis)),
            fontSize = 72.sp,
            fontWeight = FontWeight.Thin,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        when (val p = store.phase) {
            is AlarmPhase.Alarming -> {
                Text(
                    "Move the phone to snooze",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            is AlarmPhase.Snoozing -> {
                Text("Snoozing", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                val remainingSec = ((p.until - nowMillis) / 1000L).coerceAtLeast(0)
                Text(
                    "%02d:%02d".format(remainingSec / 60, remainingSec % 60),
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            else -> Unit
        }

        Spacer(Modifier.weight(1f))
        SlideUpHint(label = "Slide up to dismiss")
    }
}
