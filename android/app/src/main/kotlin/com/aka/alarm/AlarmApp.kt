package com.aka.alarm

import android.app.Application
import com.aka.alarm.model.AlarmStore

class AlarmApp : Application() {
    lateinit var alarmStore: AlarmStore
        private set

    override fun onCreate() {
        super.onCreate()
        alarmStore = AlarmStore(this)
    }
}
