package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id != -1) {
            Storage.setDone(context, id, true)
            Notifier.refresh(context)
        }
    }
}
