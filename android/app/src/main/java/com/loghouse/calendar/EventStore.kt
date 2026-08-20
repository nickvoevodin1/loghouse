package com.loghouse.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The events the widget draws. The web app hands them over through the JS
 * bridge on every save, so the widget never needs the network.
 */
data class WidgetEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val color: String,
    val allDay: Boolean,
    val isTask: Boolean,
    val done: Boolean
)

object EventStore {
    private const val PREFS = "loghouse_widget"
    private const val KEY_EVENTS = "events"
    private const val KEY_DAY = "day"

    fun save(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_EVENTS, json).apply()
    }

    /** Marks one item done, so the tick in the widget survives until the app catches up. */
    fun toggleDone(context: Context, id: String) {
        val items = load(context).map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        val arr = JSONArray()
        items.forEach { ev ->
            arr.put(JSONObject().apply {
                put("id", ev.id)
                put("title", ev.title)
                put("start", ev.start)
                put("end", ev.end)
                put("color", ev.color)
                put("allDay", ev.allDay)
                put("isTask", ev.isTask)
                put("done", ev.done)
            })
        }
        save(context, arr.toString())
        pendingDone(context, id)
    }

    /** Ticks made in the widget wait here until the web app is next opened. */
    fun pendingDone(context: Context, id: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = p.getString("pending", "") ?: ""
        val set = cur.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (!set.add(id)) set.remove(id)
        p.edit().putString("pending", set.joinToString(",")).apply()
    }

    fun takePending(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = p.getString("pending", "") ?: ""
        p.edit().putString("pending", "").apply()
        return cur
    }

    fun saveDay(context: Context, dow: String, date: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DAY, "$dow|$date").apply()
    }

    fun day(context: Context): Pair<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DAY, "") ?: ""
        val parts = raw.split("|")
        return if (parts.size == 2) parts[0] to parts[1] else "" to ""
    }

    fun load(context: Context): List<WidgetEvent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WidgetEvent(
                    id = o.optString("id"),
                    title = o.optString("title").ifBlank { "Без названия" },
                    start = o.optString("start"),
                    end = o.optString("end"),
                    color = o.optString("color", "#7C6DF2"),
                    allDay = o.optBoolean("allDay"),
                    isTask = o.optBoolean("isTask"),
                    done = o.optBoolean("done")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
