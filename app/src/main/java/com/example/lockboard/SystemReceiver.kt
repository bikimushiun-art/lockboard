package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Перезагрузка, смена даты/часового пояса: пересоздаём уведомления. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.refresh(context)
    }
}
