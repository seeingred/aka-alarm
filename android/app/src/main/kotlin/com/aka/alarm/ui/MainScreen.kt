package com.aka.alarm.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.aka.alarm.Tuning
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule
import com.aka.alarm.model.AlarmStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(store: AlarmStore, onStart: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when (store.phase.kind) {
            AlarmPhase.Kind.IDLE -> SetAlarmView(store, onStart)
            AlarmPhase.Kind.ARMED,
            AlarmPhase.Kind.MONITORING,
            AlarmPhase.Kind.IN_WINDOW -> MonitoringView(store)
            else -> Box(Modifier.fillMaxSize())
        }

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        if (showSettings) {
            // Three sections are taller than half the screen, and a sheet that
            // opens half-expanded hides the bottom one behind a drag nobody
            // expects. Open it at full content height instead.
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                SettingsSheet(store)
            }
        }
    }
}

// MARK: - Settings

@Composable
private fun SettingsSheet(store: AlarmStore) {
    val micActive = store.phase.kind == AlarmPhase.Kind.MONITORING ||
        store.phase.kind == AlarmPhase.Kind.IN_WINDOW

    Column(
        Modifier
            .fillMaxWidth()
            // Three sections no longer fit a landscape phone's sheet height.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Text("Sensitivity", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "How easily sound above the room's baseline triggers the alarm. " +
                "Trigger point: +%.1f dB over baseline.".format(
                    Tuning.spikeThresholdDb(store.sensitivity)
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        if (micActive) {
            Spacer(Modifier.height(16.dp))
            MicLevelBar(
                currentDb = store.micLevelDb,
                baselineDb = store.baselineDb,
                thresholdDb = store.baselineDb + Tuning.spikeThresholdDb(store.sensitivity),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        // 15 discrete positions → 0.5 dB per step across the 1–8 dB threshold
        // range. Discrete steps keep persisted values exact (a continuous M3
        // Slider pixel-snaps its value and fires a spurious onValueChange on
        // first composition, silently overwriting the stored default).
        Slider(
            value = store.sensitivity,
            onValueChange = { store.updateSensitivity(it) },
            steps = 13,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Very low",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                "Very high",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Start listening", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "When the microphone turns on: ${
                Tuning.activationLeadLabel(store.activationLeadMinutes)
            }. A later start saves battery overnight; the alarm always fires by " +
                "the end of the window either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(8.dp))
        // Discrete positions over Tuning.ACTIVATION_LEAD_OPTIONS_MINUTES, for the
        // same reason the sensitivity slider is stepped (see comment above).
        val leadOptions = Tuning.ACTIVATION_LEAD_OPTIONS_MINUTES
        val leadIndex = leadOptions.indexOf(store.activationLeadMinutes).coerceAtLeast(0)
        Slider(
            value = leadIndex.toFloat(),
            onValueChange = { raw ->
                val idx = raw.roundToInt().coerceIn(0, leadOptions.lastIndex)
                store.updateActivationLead(leadOptions[idx])
            },
            valueRange = 0f..leadOptions.lastIndex.toFloat(),
            steps = leadOptions.size - 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Right away",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                "5 min before",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.height(24.dp))
        AlarmSoundSection(store)
    }
}

@Composable
private fun AlarmSoundSection(store: AlarmStore) {
    var pickerMissing by remember { mutableStateOf(false) }

    // Two routes to a sound, because they cover different things: the system
    // picker lists the device's alarm tones (world-readable, no permission),
    // while a user's own file comes through SAF, whose read grant covers the
    // copy we take. The ringtone picker's own "Add ringtone" entry would hand
    // back a media-store URI we can't read without a storage permission.
    val pickSystemSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(
                it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java,
            )
        }
        if (uri != null) store.setCustomAlarmSound(uri)
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) store.setCustomAlarmSound(uri) }

    val openSystemPicker = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        try {
            pickSystemSound.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pickerMissing = true
        }
    }

    Text("Alarm sound", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    // The current pick as a chip: a value token rather than another line of
    // text. Tapping it opens the system picker; the × on a custom sound is
    // the standard "remove" affordance and returns to the built-in tone.
    val removeIcon: (@Composable () -> Unit)? = store.alarmSoundName?.let {
        {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClickLabel = "Use the built-in tone") {
                        store.clearCustomAlarmSound()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Use the built-in tone",
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            }
        }
    }
    InputChip(
        selected = true,
        onClick = openSystemPicker,
        label = { Text(store.alarmSoundName ?: "Built-in tone") },
        leadingIcon = {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        trailingIcon = removeIcon,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Whichever you pick fades in from silence over a minute.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    val problem = store.alarmSoundError
        ?: if (pickerMissing) "This device has no sound picker; choose an audio file instead." else null
    if (problem != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            problem,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetButton("System sounds…", Modifier.weight(1f)) { openSystemPicker() }
        SheetButton("Audio file…", Modifier.weight(1f)) {
            pickFile.launch(arrayOf("audio/*"))
        }
    }
}

/** Pill button in the Start button's translucent style, sized for the sheet. */
@Composable
private fun SheetButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// MARK: - Set Alarm

/**
 * Below this content height the five-row wheels, window label and Start
 * button no longer fit stacked: landscape phones, split screen, and 16:9
 * budget phones in portrait.
 */
private val STACKED_LAYOUT_MIN_HEIGHT = 640.dp

@Composable
private fun SetAlarmView(store: AlarmStore, onStart: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val short = maxHeight < STACKED_LAYOUT_MIN_HEIGHT
        // Whole rows when height is tight. NumberWheel also copes with being
        // squeezed to any height (see its padding logic); this just keeps the
        // wheel an odd number of full rows so it reads as a wheel.
        val visibleRows = if (short) 3 else 5

        if (short && maxWidth > maxHeight) {
            // Landscape phone: wheels left, label + Start right. Stacked, the
            // label and the Start button fell off the bottom of the screen
            // (issue #4's screenshots).
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WindowTitle()
                    Spacer(Modifier.height(16.dp))
                    WheelPair(store, visibleRows)
                }
                Spacer(Modifier.width(24.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WindowLabel(store)
                    Spacer(Modifier.height(32.dp))
                    StartButton(onStart)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                WindowTitle()
                Spacer(Modifier.height(24.dp))
                WheelPair(store, visibleRows)
                Spacer(Modifier.height(24.dp))
                WindowLabel(store)
                Spacer(Modifier.weight(1f))
                StartButton(onStart)
            }
        }
    }
}

@Composable
private fun WindowTitle() {
    Text(
        text = "Wake-up window",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun WheelPair(store: AlarmStore, visibleRows: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        NumberWheel(
            values = (0..23).toList(),
            selection = store.selectedHour,
            onSelectionChange = { store.selectedHour = it },
            visibleRows = visibleRows,
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
            visibleRows = visibleRows,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WindowLabel(store: AlarmStore) {
    AutoShrinkText(
        text = formatWindowLabel(store.selectedHour, store.selectedMinute),
        fontSize = 56.sp,
        fontWeight = FontWeight.Thin,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StartButton(onStart: () -> Unit) {
    Button(
        onClick = onStart,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
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

/**
 * Single-line text that shrinks itself until it fits the available width —
 * Android counterpart of the iOS `minimumScaleFactor` treatment. Needed
 * because large system font scales make the fixed-size time labels wrap and
 * overlap. The scale only ever decreases (and survives per-second clock text
 * changes) so it settles after a few frames instead of flickering.
 */
@Composable
private fun AutoShrinkText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    Text(
        text = text,
        fontSize = fontSize * scale,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { if (it.didOverflowWidth) scale *= 0.92f },
        modifier = modifier,
    )
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
        AutoShrinkText(
            text = tFmt.format(Date(nowMillis)),
            fontSize = 64.sp,
            fontWeight = FontWeight.Thin,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        val wFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        store.phase.window?.let { (start, end) ->
            Text(
                "${wFmt.format(Date(start))} – ${wFmt.format(Date(end))}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(24.dp))

        if (store.phase.kind != AlarmPhase.Kind.ARMED) {
            MicLevelBar(
                currentDb = store.micLevelDb,
                baselineDb = store.baselineDb,
                thresholdDb = store.baselineDb + Tuning.spikeThresholdDb(store.sensitivity),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(80.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        val statusText = when (store.phase.kind) {
            AlarmPhase.Kind.ARMED -> store.phase.window?.first?.let { start ->
                "Alarm armed — mic off until ${
                    wFmt.format(
                        Date(
                            AlarmSchedule.baselineStartMillis(
                                start,
                                store.activationLeadMinutes,
                            )
                        )
                    )
                }"
            } ?: "Alarm armed — mic off"
            AlarmPhase.Kind.IN_WINDOW -> "Listening for stirring…"
            else -> "Learning room baseline…"
        }
        Text(
            text = statusText,
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
    thresholdDb: Double? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val curFrac = normalize(currentDb).toFloat()
        val baselineFrac = normalize(baselineDb).toFloat()
        val onSurface = MaterialTheme.colorScheme.onSurface

        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
        // Level
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(curFrac)
                .background(
                    color = onSurface.copy(alpha = 0.32f),
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
        // Trigger marker — where a peak has to reach to fire the alarm. Hidden
        // until the baseline has climbed above the display floor.
        if (thresholdDb != null && baselineDb > Tuning.DISPLAY_DB_FLOOR) {
            val thresholdFrac = normalize(thresholdDb).toFloat()
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .offset { IntOffset((maxWidthPx * thresholdFrac).roundToInt(), 0) }
                    .background(Color(0xFFFF5252))
            )
        }
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
