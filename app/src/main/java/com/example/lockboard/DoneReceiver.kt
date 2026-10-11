package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Нажатие на строку задачи: выполнено / не выполнено. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id != -1) Storage.toggleDone(context, id)
        Notifier.refresh(context, setOf(Notifier.Part.TASKS)) // только блок задач
    }
}
