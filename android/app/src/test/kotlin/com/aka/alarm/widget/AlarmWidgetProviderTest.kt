package com.aka.alarm.widget

import com.aka.alarm.model.AlarmPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmWidgetProviderTest {

    @Test
    fun actionToggleArm_isCorrectConstant() {
        assertEquals("com.aka.alarm.widget.ACTION_TOGGLE_ARM", AlarmWidgetProvider.ACTION_TOGGLE_ARM)
    }

    @Test
    fun formatTimeRange_whenNullStore_returnsDefault() {
        val formatted = AlarmWidgetProvider.formatTimeRange(null)
        assertEquals("07:00 – 07:30", formatted)
    }

    @Test
    fun formatTimeRange_whenPhaseHasWindow_formatsStartAndEnd() {
        val startMillis = 1700000000000L
        val endMillis = startMillis + 30 * 60 * 1000L
        val phase = AlarmPhase.Armed(startMillis, endMillis)

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val expected = "${fmt.format(Date(startMillis))} – ${fmt.format(Date(endMillis))}"

        val window = phase.window
        val actual = if (window != null) {
            "${fmt.format(Date(window.first))} – ${fmt.format(Date(window.second))}"
        } else {
            ""
        }

        assertEquals(expected, actual)
    }
}
