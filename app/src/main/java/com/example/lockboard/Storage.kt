package com.example.lockboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Task(val id: Int, val text: String, val done: Boolean)

object Storage {
    private const val PREFS = "lockboard"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- заметка ----
    fun getNote(c: Context): String = prefs(c).getString("note", "") ?: ""
    fun setNote(c: Context, text: String) = prefs(c).edit().putString("note", text).apply()
    fun appendNote(c: Context, line: String) {
        val cur = getNote(c)
        setNote(c, if (cur.isBlank()) line else cur + "\n" + line)
    }

    // ---- трекер недели: отмеченные даты (ISO, например 2026-10-11) ----
    fun getChecked(c: Context): Set<String> =
        (prefs(c).getStringSet("days", emptySet()) ?: emptySet()).toSet()

    fun toggleDay(c: Context, date: String) {
        val s = getChecked(c).toMutableSet()
        if (!s.add(date)) s.remove(date)
        prefs(c).edit().putStringSet("days", s).apply()
    }

    // ---- задачи ----
    fun getTasks(c: Context): List<Task> {
        val arr = JSONArray(prefs(c).getString("tasks", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Task(o.getInt("id"), o.getString("text"), o.getBoolean("done"))
        }
    }

    private fun save(c: Context, tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done))
        }
        prefs(c).edit().putString("tasks", arr.toString()).apply()
    }

    fun addTask(c: Context, text: String) {
        val p = prefs(c)
        val id = p.getInt("next_id", 100)
        p.edit().putInt("next_id", id + 1).apply()
        save(c, getTasks(c) + Task(id, text, false))
    }

    fun setDone(c: Context, id: Int, done: Boolean) =
        save(c, getTasks(c).map { if (it.id == id) it.copy(done = done) else it })

    fun deleteDone(c: Context) = save(c, getTasks(c).filter { !it.done })
}
