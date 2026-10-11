package com.example.lockboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Tracker(val id: Int, val name: String, val color: Int)
data class Group(val id: Int, val name: String)
data class Task(val id: Int, val groupId: Int, val text: String, val done: Boolean)

object Storage {
    // Кэши в памяти: JSON разбирается один раз, а не при каждом нажатии в уведомлении
    @Volatile private var tCache: List<Tracker>? = null
    @Volatile private var gCache: List<Group>? = null
    @Volatile private var kCache: List<Task>? = null
    @Volatile private var cCache: Map<Int, Set<String>>? = null

    private fun p(c: Context) = c.getSharedPreferences("lockboard", Context.MODE_PRIVATE)

    private fun nextId(c: Context): Int {
        val id = p(c).getInt("next_id", 100)
        p(c).edit().putInt("next_id", id + 1).apply()
        return id
    }

    // ---------- заметка ----------
    fun getNote(c: Context): String = p(c).getString("note", "") ?: ""
    fun setNote(c: Context, text: String) = p(c).edit().putString("note", text).apply()
    fun appendNote(c: Context, line: String) {
        val cur = getNote(c)
        setNote(c, if (cur.isBlank()) line else cur + "\n" + line)
    }

    // ---------- трекеры ----------
    fun getTrackers(c: Context): List<Tracker> {
        tCache?.let { return it }
        if (!p(c).contains("trackers")) {
            // первый запуск (или обновление со старой версии): один трекер, старые отметки переносим
            val t = Tracker(nextId(c), "Привычка", 0xFFFF6A13.toInt())
            saveTrackers(c, listOf(t))
            val old = p(c).getStringSet("days", emptySet()) ?: emptySet()
            if (old.isNotEmpty()) {
                val o = JSONObject().put(t.id.toString(), JSONArray(old.toList()))
                p(c).edit().putString("checks", o.toString()).apply()
                cCache = null
            }
        }
        val arr = JSONArray(p(c).getString("trackers", "[]"))
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Tracker(o.getInt("id"), o.getString("name"), o.getInt("color"))
        }
        tCache = list
        return list
    }

    private fun saveTrackers(c: Context, list: List<Tracker>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        p(c).edit().putString("trackers", arr.toString()).apply()
        tCache = list
    }

    fun addTracker(c: Context, name: String, color: Int) =
        saveTrackers(c, getTrackers(c) + Tracker(nextId(c), name, color))

    fun updateTracker(c: Context, id: Int, name: String, color: Int) =
        saveTrackers(c, getTrackers(c).map { if (it.id == id) Tracker(id, name, color) else it })

    fun deleteTracker(c: Context, id: Int) {
        saveTrackers(c, getTrackers(c).filter { it.id != id })
        val m = allChecks(c).toMutableMap()
        m.remove(id)
        saveChecks(c, m)
    }

    private fun allChecks(c: Context): Map<Int, Set<String>> {
        cCache?.let { return it }
        val o = JSONObject(p(c).getString("checks", "{}") ?: "{}")
        val m = HashMap<Int, Set<String>>()
        o.keys().forEach { k ->
            val a = o.getJSONArray(k)
            m[k.toInt()] = (0 until a.length()).map { a.getString(it) }.toSet()
        }
        cCache = m
        return m
    }

    private fun saveChecks(c: Context, m: Map<Int, Set<String>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), JSONArray(v.toList())) }
        p(c).edit().putString("checks", o.toString()).apply()
        cCache = m
    }

    fun getChecks(c: Context, trackerId: Int): Set<String> = allChecks(c)[trackerId] ?: emptySet()

    fun toggleDay(c: Context, trackerId: Int, date: String) {
        val m = allChecks(c).toMutableMap()
        val set = (m[trackerId] ?: emptySet()).toMutableSet()
        if (!set.add(date)) set.remove(date)
        m[trackerId] = set
        saveChecks(c, m)
    }

    // ---------- группы и задачи ----------
    fun getGroups(c: Context): List<Group> {
        gCache?.let { return it }
        if (!p(c).contains("groups")) saveGroups(c, listOf(Group(nextId(c), "Задачи")))
        val arr = JSONArray(p(c).getString("groups", "[]"))
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Group(o.getInt("id"), o.getString("name"))
        }
        gCache = list
        return list
    }

    private fun saveGroups(c: Context, list: List<Group>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        p(c).edit().putString("groups", arr.toString()).apply()
        gCache = list
    }

    fun addGroup(c: Context, name: String) = saveGroups(c, getGroups(c) + Group(nextId(c), name))

    fun renameGroup(c: Context, id: Int, name: String) =
        saveGroups(c, getGroups(c).map { if (it.id == id) Group(id, name) else it })

    fun deleteGroup(c: Context, id: Int) {
        val tasks = getTasks(c).filter { it.groupId != id }
        saveGroups(c, getGroups(c).filter { it.id != id })
        saveTasks(c, tasks)
    }

    fun getTasks(c: Context): List<Task> {
        kCache?.let { return it }
        val fallback = getGroups(c).firstOrNull()?.id ?: -1 // старые задачи без группы
        val arr = JSONArray(p(c).getString("tasks", "[]"))
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Task(o.getInt("id"), o.optInt("group", fallback), o.getString("text"), o.getBoolean("done"))
        }
        kCache = list
        return list
    }

    private fun saveTasks(c: Context, tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("group", it.groupId).put("text", it.text).put("done", it.done))
        }
        p(c).edit().putString("tasks", arr.toString()).apply()
        kCache = tasks
    }

    fun addTask(c: Context, groupId: Int, text: String) =
        saveTasks(c, getTasks(c) + Task(nextId(c), groupId, text, false))

    fun renameTask(c: Context, id: Int, text: String) =
        saveTasks(c, getTasks(c).map { if (it.id == id) it.copy(text = text) else it })

    fun setDone(c: Context, id: Int, done: Boolean) =
        saveTasks(c, getTasks(c).map { if (it.id == id) it.copy(done = done) else it })

    fun toggleDone(c: Context, id: Int) =
        saveTasks(c, getTasks(c).map { if (it.id == id) it.copy(done = !it.done) else it })

    fun deleteTask(c: Context, id: Int) = saveTasks(c, getTasks(c).filter { it.id != id })

    fun deleteDone(c: Context) = saveTasks(c, getTasks(c).filter { !it.done })

    // ---------- учёт показанных уведомлений (чтобы убирать удалённые) ----------
    fun getPosted(c: Context): Set<Int> =
        (p(c).getStringSet("posted", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()

    fun setPosted(c: Context, ids: Set<Int>) {
        if (ids == getPosted(c)) return
        p(c).edit().putStringSet("posted", ids.map { it.toString() }.toSet()).apply()
    }
}
