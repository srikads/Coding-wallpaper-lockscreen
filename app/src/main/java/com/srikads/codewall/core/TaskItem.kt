package com.srikads.codewall.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * A to-do stored on the device.
 *
 * @param date ISO date (yyyy-MM-dd) the task is for, or null for "until done".
 * @param daily repeats every day; completion only counts for [doneOn].
 * @param doneOn ISO date the task was completed, or null if open.
 */
data class TaskItem(
    val id: Long,
    val title: String,
    val date: String? = null,
    val daily: Boolean = false,
    val doneOn: String? = null,
) {
    fun isDone(today: String): Boolean = if (daily) doneOn == today else doneOn != null

    /** Whether the task belongs on [today]'s list. ISO dates compare correctly as strings. */
    fun isForToday(today: String): Boolean = when {
        daily -> true
        doneOn != null -> doneOn == today
        date == null -> true
        else -> date <= today // overdue tasks stay visible
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("daily", daily).apply {
        date?.let { put("date", it) }
        doneOn?.let { put("doneOn", it) }
    }

    companion object {
        fun listFromJson(json: String?): List<TaskItem> = runCatching {
            val arr = JSONArray(json ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TaskItem(
                    id = o.getLong("id"),
                    title = o.optString("title"),
                    date = o.optString("date").ifBlank { null },
                    daily = o.optBoolean("daily"),
                    doneOn = o.optString("doneOn").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())

        fun listToJson(tasks: List<TaskItem>): String = JSONArray().apply { tasks.forEach { put(it.toJson()) } }.toString()

        /** Today's tasks as JSON, open ones first. */
        fun todayValue(tasks: List<TaskItem>, today: String, showDone: Boolean): JValue = JArray(
            tasks.filter { it.isForToday(today) && (showDone || !it.isDone(today)) }
                .sortedBy { it.isDone(today) }
                .map { obj("done" to JBool(it.isDone(today)), "title" to JString(it.title), inline = true) }
        )
    }
}
