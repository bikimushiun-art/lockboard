package com.example.lockboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class NoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("clear", false)) {
            Storage.setNote(context, "")
        } else {
            val text = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(Notifier.KEY_NOTE)?.toString()?.trim()
            if (!text.isNullOrEmpty()) Storage.appendNote(context, text)
        }
        Notifier.refresh(context, setOf(Notifier.Part.NOTE)) // только заметка; заодно гасит индикатор отправки
    }
}
