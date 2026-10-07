package com.srikads.codewall.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.time.LocalDate

/**
 * Today's steps from the hardware step counter. The sensor reports steps since boot,
 * so we store the reading at the start of each day as a baseline (locally).
 */
class StepCounter(context: Context) : SensorEventListener {
    private val app = context.applicationContext
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val prefs = app.getSharedPreferences("steps", Context.MODE_PRIVATE)
    private var registered = false

    @Volatile var today: Int? = null
        private set

    val available: Boolean get() = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun start() {
        if (registered || !Permissions.activity(app)) return
        val sm = sensors ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (registered) sensors?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val total = event.values[0].toLong()
        val day = LocalDate.now().toString()
        var baseDay = prefs.getString("day", null)
        var base = prefs.getLong("base", -1)
        // New day, first run, or the counter reset after a reboot.
        if (baseDay != day || base < 0 || total < base) {
            base = if (baseDay == day && total < base) 0 else total
            baseDay = day
            prefs.edit().putString("day", day).putLong("base", base).apply()
        }
        today = (total - base).toInt()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
