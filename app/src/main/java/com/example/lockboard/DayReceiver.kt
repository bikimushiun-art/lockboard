package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("date")?.let { Storage.toggleDay(context, it) }
        Notifier.refresh(context)
    }
}
