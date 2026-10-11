package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tracker = intent.getIntExtra("tracker", -1)
        val date = intent.getStringExtra("date")
        if (tracker != -1 && date != null) Storage.toggleDay(context, tracker, date)
        Notifier.refresh(context, setOf(Notifier.Part.TRACKERS)) // обновляем только блок трекеров
    }
}
