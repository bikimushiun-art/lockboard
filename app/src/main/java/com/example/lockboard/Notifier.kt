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
import android.view.View
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
    private const val NOTE_ID = 1
    private const val SUMMARY_BASE = 50000
    private const val TRACKER_BASE = 100000
    private const val MAX_TASKS = 20
    const val KEY_NOTE = "note_text"

    private val F = intArrayOf(R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5, R.id.f6)
    private val B = intArrayOf(R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6)
    private val R_ = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5, R.id.r6)
    private val X = intArrayOf(R.id.x0, R.id.x1, R.id.x2, R.id.x3, R.id.x4, R.id.x5, R.id.x6)

    fun refresh(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(c)
        val nm = NotificationManagerCompat.from(c)
        val posted = mutableSetOf<Int>()

        // 1. Трекеры: каждый — отдельное уведомление. Свёрнутый и развёрнутый вид одинаковые.
        Storage.getTrackers(c).forEach { t ->
            val id = TRACKER_BASE + t.id
            nm.notify(id, base(c, "1")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(trackerViews(c, t))
                .setCustomBigContentView(trackerViews(c, t))
                .build())
            posted += id
        }

        // 2. Заметка: «Заметка» дописывает строку, «Очистить» стирает
        val note = Storage.getNote(c).ifBlank { "Заметка пуста" }
        val notePi = PendingIntent.getBroadcast(
            c, 10, Intent(c, NoteReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val noteAction = NotificationCompat.Action.Builder(0, "Заметка", notePi)
            .addRemoteInput(RemoteInput.Builder(KEY_NOTE).setLabel("Добавить в заметку").build())
            .setAllowGeneratedReplies(false)
            .build()
        val clearPi = PendingIntent.getBroadcast(
            c, 11, Intent(c, NoteReceiver::class.java).putExtra("clear", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(NOTE_ID, base(c, "2")
            .setContentText(note)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note))
            .addAction(noteAction)
            .addAction(0, "Очистить", clearPi)
            .build())
        posted += NOTE_ID

        // 3. Задачи по группам: у каждой задачи название группы в шапке, плюс общая сводка группы
        val groups = Storage.getGroups(c)
        var shown = 0
        val active = Storage.getTasks(c).filter { !it.done }
        for (g in groups) {
            val items = active.filter { it.groupId == g.id }.take(MAX_TASKS - shown)
            if (items.isEmpty()) continue
            shown += items.size
            val key = "task_g${g.id}"
            items.forEach { t ->
                val pi = PendingIntent.getBroadcast(
                    c, t.id, Intent(c, DoneReceiver::class.java).putExtra("id", t.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                nm.notify(t.id, base(c, "3")
                    .setSubText(g.name)
                    .setContentText(t.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(t.text))
                    .setGroup(key)
                    .addAction(0, "Готово", pi)
                    .build())
                posted += t.id
            }
            val inbox = NotificationCompat.InboxStyle()
            items.forEach { inbox.addLine(it.text) }
            nm.notify(SUMMARY_BASE + g.id, base(c, "3")
                .setContentTitle(g.name)
                .setContentText("Задач: ${items.size}")
                .setStyle(inbox)
                .setGroup(key)
                .setGroupSummary(true)
                .build())
            posted += SUMMARY_BASE + g.id
        }

        // убрать уведомления того, чего больше нет
        (Storage.getPosted(c) - posted).forEach { nm.cancel(it) }
        Storage.setPosted(c, posted)
    }

    private fun trackerViews(c: Context, t: Tracker): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_tracker)
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val checked = Storage.getChecks(c, t.id)
        val letters = "ПВСЧПСВ"
        rv.setTextViewText(R.id.name, t.name)
        rv.setTextColor(R.id.name, t.color)
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val on = d.toString() in checked
            rv.setImageViewResource(B[i], if (on) R.drawable.dot_fill else R.drawable.dot_ring)
            rv.setInt(B[i], "setColorFilter", t.color)
            rv.setViewVisibility(R_[i], if (d == today) View.VISIBLE else View.INVISIBLE)
            rv.setTextViewText(X[i], if (on) "✓" else letters[i].toString())
            rv.setTextColor(X[i], if (on) Color.WHITE else t.color)
            val pi = PendingIntent.getBroadcast(
                c, t.id * 10 + i,
                Intent(c, DayReceiver::class.java).putExtra("tracker", t.id).putExtra("date", d.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(F[i], pi)
        }
        return rv
    }

    private fun base(c: Context, sort: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSortKey(sort)
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
