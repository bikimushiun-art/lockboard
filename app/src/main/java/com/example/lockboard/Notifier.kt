package com.example.lockboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object Notifier {
    private const val CHANNEL = "lockboard_v3"
    private const val TRACKER_ID = 2
    private const val NOTE_ID = 1
    private const val MAX_TASKS = 8
    const val KEY_NOTE = "note_text"
    private val ORANGE = Color.parseColor("#FF6A13")

    private val ROOT = intArrayOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6)
    private val LET = intArrayOf(R.id.l0, R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6)
    private val CIR = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6)
    private val DAT = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6)

    fun refresh(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(c)
        val nm = NotificationManagerCompat.from(c)

        // 1. Трекер недели
        nm.notify(TRACKER_ID, base(c)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(trackerViews(c))
            .setCustomBigContentView(trackerViews(c))
            .build())

        // 2. Заметка: кнопка «Заметка» с полем ввода, ввод дописывается в конец
        val note = Storage.getNote(c).ifBlank { "Заметка пуста" }
        val noteIntent = Intent(c, NoteReceiver::class.java)
        val notePi = PendingIntent.getBroadcast(
            c, 10, noteIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val noteAction = NotificationCompat.Action.Builder(0, "Заметка", notePi)
            .addRemoteInput(RemoteInput.Builder(KEY_NOTE).setLabel("Добавить в заметку").build())
            .setAllowGeneratedReplies(false)
            .build()
        val clearPi = PendingIntent.getBroadcast(
            c, 11, Intent(c, NoteReceiver::class.java).putExtra("clear", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(NOTE_ID, base(c)
            .setContentText(note)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note))
            .addAction(noteAction)
            .addAction(0, "Очистить", clearPi)
            .build())

        // 3. Задачи
        val tasks = Storage.getTasks(c)
        tasks.filter { it.done }.forEach { nm.cancel(it.id) }
        tasks.filter { !it.done }.take(MAX_TASKS).forEach { t ->
            val pi = PendingIntent.getBroadcast(
                c, t.id, Intent(c, DoneReceiver::class.java).putExtra("id", t.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            nm.notify(t.id, base(c)
                .setContentText(t.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(t.text))
                .setGroup("tasks")
                .addAction(0, "Готово", pi)
                .build())
        }
    }

    private fun trackerViews(c: Context): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_tracker)
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val checked = Storage.getChecked(c)
        val letters = "ПВСЧПСВ"
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val isToday = d == today
            val on = d.toString() in checked
            rv.setTextViewText(LET[i], letters[i].toString())
            rv.setTextViewText(DAT[i], d.dayOfMonth.toString())
            rv.setTextViewText(CIR[i], if (on) "✓" else "")
            rv.setInt(CIR[i], "setBackgroundResource",
                when {
                    isToday -> R.drawable.circle_today
                    on -> R.drawable.circle_on
                    else -> R.drawable.circle_off
                })
            if (isToday) {
                rv.setTextColor(LET[i], ORANGE)
                rv.setTextColor(DAT[i], ORANGE)
            }
            val pi = PendingIntent.getBroadcast(
                c, 1000 + i, Intent(c, DayReceiver::class.java).putExtra("date", d.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(ROOT[i], pi)
        }
        return rv
    }

    private fun base(c: Context): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
    }

    private fun createChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("lockboard")
        nm.deleteNotificationChannel("lockboard_v2")
        val ch = NotificationChannel(CHANNEL, "Локскрин", NotificationManager.IMPORTANCE_DEFAULT).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }
}
