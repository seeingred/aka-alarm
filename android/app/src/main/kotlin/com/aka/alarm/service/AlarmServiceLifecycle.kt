package com.aka.alarm.service

import com.aka.alarm.model.AlarmPhase

enum class ServiceStartCommandAction {
    StopImmediately,
    PromoteToForeground,
}

object AlarmServiceLifecycle {

    fun shouldStartForegroundService(currentPhase: AlarmPhase, nextPhase: AlarmPhase): Boolean {
        val previouslyActive = currentPhase !is AlarmPhase.Idle
        val nextActive = nextPhase !is AlarmPhase.Idle
        return nextActive && !previouslyActive
    }

    fun shouldStopService(currentPhase: AlarmPhase, nextPhase: AlarmPhase): Boolean {
        val previouslyActive = currentPhase !is AlarmPhase.Idle
        val nextActive = nextPhase !is AlarmPhase.Idle
        return !nextActive && previouslyActive
    }

    fun startCommandAction(phaseAtStart: AlarmPhase): ServiceStartCommandAction =
        if (phaseAtStart is AlarmPhase.Idle) {
            ServiceStartCommandAction.StopImmediately
        } else {
            ServiceStartCommandAction.PromoteToForeground
        }

    fun shouldUpdateNotification(phase: AlarmPhase): Boolean =
        phase !is AlarmPhase.Idle
}
