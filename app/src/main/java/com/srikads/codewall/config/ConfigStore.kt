package com.srikads.codewall.config

import android.content.Context
import android.content.SharedPreferences
import com.srikads.codewall.core.TaskItem
import com.srikads.codewall.core.WallConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * All settings and tasks live in one private SharedPreferences file.
 * Nothing is synced or backed up (see allowBackup=false in the manifest).
 */
class ConfigStore(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun config(): WallConfig = WallConfig.fromJson(prefs.getString(KEY_CONFIG, null))

    fun saveConfig(config: WallConfig) {
        prefs.edit().putString(KEY_CONFIG, config.toJson().toString()).apply()
    }

    fun update(block: (WallConfig) -> WallConfig) = saveConfig(block(config()))

    fun tasks(): List<TaskItem> = TaskItem.listFromJson(prefs.getString(KEY_TASKS, null))

    fun saveTasks(tasks: List<TaskItem>) {
        prefs.edit().putString(KEY_TASKS, TaskItem.listToJson(tasks)).apply()
    }

    /** Emits once immediately and again whenever settings or tasks change. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CONFIG || key == KEY_TASKS) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val FILE = "codewall"
        const val KEY_CONFIG = "config"
        const val KEY_TASKS = "tasks"
    }
}
