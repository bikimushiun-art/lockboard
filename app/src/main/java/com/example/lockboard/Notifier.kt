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
    private const val TRACKERS_ID = 2
    private const val TASKS_ID = 3
    private const val MAX_TASKS = 12
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

        // 1. Все трекеры в одном уведомлении. Свёрнутый и развёрнутый вид одинаковые.
        val trackers = Storage.getTrackers(c)
        if (trackers.isNotEmpty()) {
            nm.notify(TRACKERS_ID, base(c, "1")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(trackersViews(c, trackers))
                .setCustomBigContentView(trackersViews(c, trackers))
                .build())
            posted += TRACKERS_ID
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

        // 3. Все задачи всех групп в одном блоке; кружок слева отмечает задачу выполненной
        val groups = Storage.getGroups(c)
        val active = Storage.getTasks(c).filter { !it.done }
        if (active.isNotEmpty()) {
            nm.notify(TASKS_ID, base(c, "3")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(tasksViews(c, groups, active))
                .setCustomBigContentView(tasksViews(c, groups, active))
                .build())
            posted += TASKS_ID
        }

        // убрать уведомления, которых больше нет (старые отдельные карточки задач тоже)
        (Storage.getPosted(c) - posted).forEach { nm.cancel(it) }
        Storage.setPosted(c, posted)
    }

    private fun trackersViews(c: Context, trackers: List<Tracker>): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_trackers)
        trackers.forEach { rv.addView(R.id.container, trackerRow(c, it)) }
        return rv
    }

    private fun trackerRow(c: Context, t: Tracker): RemoteViews {
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

    private fun tasksViews(c: Context, groups: List<Group>, active: List<Task>): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_tasks)
        var shown = 0
        for (g in groups) {
            val items = active.filter { it.groupId == g.id }
            if (items.isEmpty() || shown >= MAX_TASKS) continue
            val header = RemoteViews(c.packageName, R.layout.notif_task_header)
            header.setTextViewText(R.id.gname, g.name)
            rv.addView(R.id.tcontainer, header)
            for (t in items) {
                if (shown >= MAX_TASKS) break
                val row = RemoteViews(c.packageName, R.layout.notif_task_row)
                row.setTextViewText(R.id.ttext, t.text)
                val pi = PendingIntent.getBroadcast(
                    c, t.id, Intent(c, DoneReceiver::class.java).putExtra("id", t.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                row.setOnClickPendingIntent(R.id.tbox, pi)
                rv.addView(R.id.tcontainer, row)
                shown++
            }
        }
        val hidden = active.size - shown
        if (hidden > 0) {
            val more = RemoteViews(c.packageName, R.layout.notif_task_header)
            more.setTextViewText(R.id.gname, "+ ещё $hidden в приложении")
            rv.addView(R.id.tcontainer, more)
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
