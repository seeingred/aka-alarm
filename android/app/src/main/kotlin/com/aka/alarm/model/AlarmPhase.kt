package com.aka.alarm.model

sealed interface AlarmPhase {
    data object Idle : AlarmPhase

    /** Armed overnight: foreground service only, mic analysis not started yet. */
    data class Armed(val start: Long, val end: Long) : AlarmPhase

    /** Mic running, building baseline; spike detection off until [start]. */
    data class Monitoring(val start: Long, val end: Long) : AlarmPhase

    data class InWindow(val start: Long, val end: Long) : AlarmPhase

    data class Alarming(val end: Long) : AlarmPhase

    data class Snoozing(val until: Long, val end: Long) : AlarmPhase

    val kind: Kind
        get() = when (this) {
            Idle -> Kind.IDLE
            is Armed -> Kind.ARMED
            is Monitoring -> Kind.MONITORING
            is InWindow -> Kind.IN_WINDOW
            is Alarming -> Kind.ALARMING
            is Snoozing -> Kind.SNOOZING
        }

    /** Window range (start, end) if this phase has one. Epoch millis. */
    val window: Pair<Long, Long>?
        get() = when (this) {
            is Armed -> start to end
            is Monitoring -> start to end
            is InWindow -> start to end
            else -> null
        }

    val windowEnd: Long?
        get() = when (this) {
            is Armed -> end
            is Monitoring -> end
            is InWindow -> end
            is Alarming -> end
            is Snoozing -> end
            Idle -> null
        }

    enum class Kind { IDLE, ARMED, MONITORING, IN_WINDOW, ALARMING, SNOOZING }
}
