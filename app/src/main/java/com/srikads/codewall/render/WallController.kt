package com.srikads.codewall.render

import android.content.Context
import android.graphics.Canvas
import com.srikads.codewall.core.DocumentBuilder
import com.srikads.codewall.core.FieldId
import com.srikads.codewall.core.JString
import com.srikads.codewall.core.JValue
import com.srikads.codewall.core.WallConfig
import com.srikads.codewall.data.DataCollector
import com.srikads.codewall.data.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glue between data, config and the renderer. One instance per wallpaper engine / preview.
 * Data is only refreshed while [start]ed (i.e. while visible) to save battery.
 */
class WallController(context: Context, private val onDataChanged: () -> Unit) {
    private val app = context.applicationContext
    val collector = DataCollector(app)
    val renderer = WallRenderer(context)

    var config: WallConfig = collector.store.config()
        private set
    /** Whether the device is currently locked; decides which fields are shown. */
    var isLocked: () -> Boolean = { false }

    @Volatile private var slowValues: Map<FieldId, JValue> = emptyMap()
    @Volatile private var weatherValue: JValue? = null
    private var scope: CoroutineScope? = null

    init {
        renderer.setConfig(config)
    }

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            collector.store.changes().collectLatest {
                config = collector.store.config()
                renderer.setConfig(config)
                if (config.isEnabled(FieldId.STEPS)) collector.steps.start() else collector.steps.stop()
                refreshSlow()
                refreshWeather()
            }
        }
        s.launch {
            while (isActive) {
                delay(SLOW_REFRESH_MS)
                refreshSlow()
            }
        }
        s.launch {
            while (isActive) {
                delay(WEATHER_CHECK_MS)
                refreshWeather()
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        collector.steps.stop()
    }

    private suspend fun refreshSlow() {
        val cfg = config
        slowValues = withContext(Dispatchers.IO) { runCatching { collector.slow(cfg, System.currentTimeMillis()) }.getOrDefault(slowValues) }
        onDataChanged()
    }

    private suspend fun refreshWeather() {
        if (!config.isEnabled(FieldId.WEATHER)) return
        weatherValue = collector.weather.get(config)
        onDataChanged()
    }

    private fun weatherOrHint(): JValue = weatherValue ?: JString(
        when {
            !config.weatherEnabled -> "disabled"
            config.manualLat == null && !Permissions.location(app) -> "grant location or set coordinates"
            else -> "loading…"
        }
    )

    /** Draws one frame and returns the delay in ms until the next one should be drawn. */
    fun frame(canvas: Canvas, width: Int, height: Int, nowMs: Long = System.currentTimeMillis()): Long {
        val clock = collector.clock(config, nowMs)
        val values = HashMap(slowValues).apply {
            putAll(clock)
            put(FieldId.WEATHER, weatherOrHint())
        }
        val locked = isLocked()
        val rendered = DocumentBuilder.render(config, values, locked)
        val time = (clock[FieldId.TIME] as? JString)?.value
        renderer.submit(rendered.lines, rendered.footer, nowMs, ignoreChangesContaining = if (config.useTemplate) time else null)
        val animating = renderer.draw(canvas, width, height, nowMs, locked)
        return nextDelay(nowMs, animating)
    }

    private fun nextDelay(nowMs: Long, animating: Boolean): Long = when {
        animating -> 33L
        config.blinkingCursor -> minOf(1000 - nowMs % 1000, 530 - nowMs % 530).coerceAtLeast(10)
        else -> (1000 - nowMs % 1000).coerceAtLeast(10)
    }

    companion object {
        private const val SLOW_REFRESH_MS = 15_000L
        private const val WEATHER_CHECK_MS = 5 * 60_000L
    }
}
