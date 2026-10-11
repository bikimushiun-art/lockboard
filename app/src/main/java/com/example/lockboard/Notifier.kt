package com.example.lockboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.style.StrikethroughSpan
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
    private const val LAUNCH_ID = 4
    private const val MAX_TASK_ROWS = 14
    private const val COLLAPSED_ROWS = 2
    const val KEY_NOTE = "note_text"

    private const val CHATGPT_PKG = "com.openai.chatgpt"
    private const val NOTES_PKG = "com.miui.notes"
    private val GREY = Color.parseColor("#8C8678")

    private val F = intArrayOf(R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5, R.id.f6)
    private val B = intArrayOf(R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6)
    private val X = intArrayOf(R.id.x0, R.id.x1, R.id.x2, R.id.x3, R.id.x4, R.id.x5, R.id.x6)

    fun refresh(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(c)
        val nm = NotificationManagerCompat.from(c)
        val posted = mutableSetOf<Int>()

        // 1. Трекеры (в свёрнутом виде — первые два, чтобы ничего не обрезалось; в развёрнутом — все)
        val trackers = Storage.getTrackers(c)
        if (trackers.isNotEmpty()) {
            nm.notify(TRACKERS_ID, base(c, "1")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(trackersViews(c, trackers.take(COLLAPSED_ROWS)))
                .setCustomBigContentView(trackersViews(c, trackers))
                .build())
            posted += TRACKERS_ID
        }

        // 2. Заметка
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

        // 3. Задачи: выполненные остаются в списке (серые, зачёркнутые), нажатие переключает
        val groups = Storage.getGroups(c)
        val tasks = Storage.getTasks(c)
        if (tasks.isNotEmpty()) {
            nm.notify(TASKS_ID, base(c, "3")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(tasksViews(c, groups, tasks, COLLAPSED_ROWS, false))
                .setCustomBigContentView(tasksViews(c, groups, tasks, MAX_TASK_ROWS, true))
                .build())
            posted += TASKS_ID
        }

        // 4. Быстрый запуск: ChatGPT (или страница в браузере) и системные Заметки
        nm.notify(LAUNCH_ID, base(c, "4")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(launchViews(c))
            .setCustomBigContentView(launchViews(c))
            .build())
        posted += LAUNCH_ID

        (Storage.getPosted(c) - posted).forEach { nm.cancel(it) }
        Storage.setPosted(c, posted)
    }

    // ---------------- трекеры ----------------

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
            val isToday = d == today
            // пусто: пунктир; отмечено: заливка без обводки;
            // сегодня: белое сплошное кольцо, а если отмечено — белая заливка
            val shape = when {
                on -> R.drawable.dot_fill
                isToday -> R.drawable.dot_ring_solid
                else -> R.drawable.dot_ring
            }
            rv.setImageViewResource(B[i], shape)
            rv.setInt(B[i], "setColorFilter", if (isToday) Color.WHITE else t.color)
            rv.setTextViewText(X[i], if (on) "✓" else letters[i].toString())
            rv.setTextColor(X[i], when {
                isToday && on -> t.color
                on -> Color.WHITE
                isToday -> Color.WHITE
                else -> t.color
            })
            val pi = PendingIntent.getBroadcast(
                c, t.id * 10 + i,
                Intent(c, DayReceiver::class.java).putExtra("tracker", t.id).putExtra("date", d.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(F[i], pi)
        }
        return rv
    }

    // ---------------- задачи ----------------

    private fun tasksViews(
        c: Context, groups: List<Group>, tasks: List<Task>, maxRows: Int, showMore: Boolean
    ): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_tasks)
        var shown = 0
        for (g in groups) {
            if (shown >= maxRows) break
            val items = tasks.filter { it.groupId == g.id }.sortedBy { it.done } // невыполненные сверху
            if (items.isEmpty()) continue
            val header = RemoteViews(c.packageName, R.layout.notif_task_header)
            header.setTextViewText(R.id.gname, "${g.name}   ${items.count { it.done }}/${items.size}")
            rv.addView(R.id.tcontainer, header)
            for (t in items) {
                if (shown >= maxRows) break
                val row = RemoteViews(c.packageName, R.layout.notif_task_row)
                if (t.done) {
                    val s = SpannableString(t.text)
                    s.setSpan(StrikethroughSpan(), 0, s.length, 0)
                    row.setTextViewText(R.id.ttext, s)
                    row.setTextColor(R.id.ttext, GREY)
                    row.setTextViewText(R.id.tbox, "✓")
                    row.setTextColor(R.id.tbox, GREY)
                } else {
                    row.setTextViewText(R.id.ttext, t.text)
                    row.setTextViewText(R.id.tbox, "○")
                }
                val pi = PendingIntent.getBroadcast(
                    c, t.id, Intent(c, DoneReceiver::class.java).putExtra("id", t.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                row.setOnClickPendingIntent(R.id.tbox, pi)
                rv.addView(R.id.tcontainer, row)
                shown++
            }
        }
        val hidden = tasks.size - shown
        if (showMore && hidden > 0) {
            val more = RemoteViews(c.packageName, R.layout.notif_task_header)
            more.setTextViewText(R.id.gname, "+ ещё $hidden в приложении")
            rv.addView(R.id.tcontainer, more)
        }
        return rv
    }

    // ---------------- быстрый запуск ----------------

    private fun launchViews(c: Context): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.notif_launch)
        // ChatGPT: приложение, если установлено, иначе сайт
        val chat = c.packageManager.getLaunchIntentForPackage(CHATGPT_PKG)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com"))
        rv.setOnClickPendingIntent(R.id.l1, activityPi(c, chat, 20))
        // Системные Заметки (Xiaomi): только если приложение найдено
        val notes = c.packageManager.getLaunchIntentForPackage(NOTES_PKG)
        if (notes != null) rv.setOnClickPendingIntent(R.id.l2, activityPi(c, notes, 21))
        else rv.setViewVisibility(R.id.l2, View.GONE)
        return rv
    }

    private fun activityPi(c: Context, intent: Intent, code: Int): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            c, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---------------- общее ----------------

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
