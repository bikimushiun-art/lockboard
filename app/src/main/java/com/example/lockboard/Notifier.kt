package com.example.lockboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL = "lockboard"
    private const val NOTE_ID = 1
    private const val MAX_TASKS = 8

    fun refresh(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(c)
        val nm = NotificationManagerCompat.from(c)
        val tasks = Storage.getTasks(c)

        // Заметка
        val note = Storage.getNote(c)
        if (note.isBlank()) nm.cancel(NOTE_ID) else {
            nm.notify(NOTE_ID, base(c)
                .setContentTitle("Заметка")
                .setContentText(note)
                .setStyle(NotificationCompat.BigTextStyle().bigText(note))
                .build())
        }

        // Задачи: выполненные убираем, активные показываем
        tasks.filter { it.done }.forEach { nm.cancel(it.id) }
        tasks.filter { !it.done }.take(MAX_TASKS).forEach { t ->
            val doneIntent = Intent(c, DoneReceiver::class.java).putExtra("id", t.id)
            val pi = PendingIntent.getBroadcast(
                c, t.id, doneIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            nm.notify(t.id, base(c)
                .setContentTitle("Задача")
                .setContentText(t.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(t.text))
                .setGroup("tasks")
                .addAction(0, "✓ Готово", pi)
                .build())
        }
    }

    private fun base(c: Context): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // показывать на локскрине
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
    }

    private fun createChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "Локскрин", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
