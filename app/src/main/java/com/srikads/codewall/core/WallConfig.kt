package com.srikads.codewall.core

import org.json.JSONArray
import org.json.JSONObject

/** Every piece of information the wallpaper can show. */
enum class FieldId(val defaultKey: String, val label: String, val description: String) {
    DATE("date", "Date", "Today's date"),
    TIME("time", "Time", "Clock with seconds"),
    WEATHER("weather", "Weather", "Open-Meteo, from rounded location"),
    TASKS("tasks", "Tasks", "Your in-app tasks for today"),
    EVENTS("events", "Calendar", "Today's calendar events"),
    NEXT_EVENT("next", "Next event", "Countdown to the next event"),
    ALARM("alarm", "Next alarm", "Next system alarm"),
    BATTERY("battery", "Battery", "Level, charging, temperature"),
    DEVICE("device", "Device", "RAM, storage, uptime"),
    STEPS("steps", "Steps", "Step counter (today)"),
    NOW_PLAYING("playing", "Now playing", "Current media (needs notification access)"),
    NETWORK("network", "Network", "Connection type and local IP"),
    QUOTE("quote", "Quote", "Rotating quote / joke / commit message"),
}

data class FieldConfig(
    val id: FieldId,
    val enabled: Boolean,
    val key: String = id.defaultKey,
    /** When false the field is hidden while the device is locked (privacy). */
    val showOnLock: Boolean = true,
)

enum class Units { METRIC, IMPERIAL }
enum class Align { LEFT, CENTER }

/** Where the quote is drawn. FOOTER puts it in its own slot, e.g. below the fingerprint icon. */
enum class QuotePlacement { INLINE, FOOTER_ON_LOCK, FOOTER_ALWAYS }

data class WallConfig(
    val fields: List<FieldConfig> = defaultFields(),
    val themeId: String = CodeTheme.DRACULA.id,
    val fontSizeSp: Float = 15f,
    /** Absolute path of a user-imported .ttf/.otf inside app storage, or null for system monospace. */
    val fontPath: String? = null,
    val bold: Boolean = false,
    /** 0 = top, 0.5 = centered, 1 = bottom of the free space inside the code area. */
    val verticalPosition: Float = 0.5f,
    /** Code area on the home screen, as fractions of the screen height. */
    val homeTop: Float = 0.08f,
    val homeBottom: Float = 0.92f,
    /** Code area on the lock screen: between the system clock and the fingerprint icon. */
    val lockTop: Float = 0.30f,
    val lockBottom: Float = 0.70f,
    /** Footer slot (used for the quote), e.g. between the fingerprint icon and the shortcuts. */
    val footerTop: Float = 0.81f,
    val footerBottom: Float = 0.91f,
    val quotePlacement: QuotePlacement = QuotePlacement.FOOTER_ON_LOCK,
    /** Render nested objects (weather, device, …) on a single, wrapped line. */
    val compactObjects: Boolean = false,
    val horizontalPaddingDp: Int = 20,
    val align: Align = Align.LEFT,
    val lineNumbers: Boolean = true,
    val header: String = "// ~/today.json",
    val use24h: Boolean = true,
    val datePattern: String = "EEE, dd MMM yyyy",
    val units: Units = Units.METRIC,
    // Animations
    val typingEffect: Boolean = true,
    val blinkingCursor: Boolean = true,
    val flashChanges: Boolean = true,
    val parallax: Boolean = true,
    // Template mode
    val useTemplate: Boolean = false,
    val template: String = DEFAULT_TEMPLATE,
    // Data
    val weatherEnabled: Boolean = true,
    val weatherRefreshMinutes: Int = 30,
    /** Optional manual coordinates; when set, GPS is never used. */
    val manualLat: Double? = null,
    val manualLon: Double? = null,
    val quoteKind: QuoteKind = QuoteKind.MIXED,
    val quoteRotateMinutes: Int = 30,
    val showCompletedTasks: Boolean = true,
) {
    val theme: CodeTheme get() = CodeTheme.byId(themeId)

    fun field(id: FieldId): FieldConfig? = fields.firstOrNull { it.id == id }
    fun isEnabled(id: FieldId): Boolean = field(id)?.enabled == true

    /** Whether the quote goes to the footer slot instead of the code (never in template mode). */
    fun quoteInFooter(locked: Boolean): Boolean = !useTemplate && when (quotePlacement) {
        QuotePlacement.INLINE -> false
        QuotePlacement.FOOTER_ON_LOCK -> locked
        QuotePlacement.FOOTER_ALWAYS -> true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("fields", JSONArray().apply {
            fields.forEach {
                put(JSONObject().put("id", it.id.name).put("enabled", it.enabled).put("key", it.key).put("lock", it.showOnLock))
            }
        })
        put("theme", themeId)
        put("fontSize", fontSizeSp.toDouble())
        fontPath?.let { put("fontPath", it) }
        put("bold", bold)
        put("vpos", verticalPosition.toDouble())
        put("homeTop", homeTop.toDouble())
        put("homeBottom", homeBottom.toDouble())
        put("lockTop", lockTop.toDouble())
        put("lockBottom", lockBottom.toDouble())
        put("footerTop", footerTop.toDouble())
        put("footerBottom", footerBottom.toDouble())
        put("quotePlacement", quotePlacement.name)
        put("compact", compactObjects)
        put("hpad", horizontalPaddingDp)
        put("align", align.name)
        put("lineNumbers", lineNumbers)
        put("header", header)
        put("use24h", use24h)
        put("datePattern", datePattern)
        put("units", units.name)
        put("typing", typingEffect)
        put("cursor", blinkingCursor)
        put("flash", flashChanges)
        put("parallax", parallax)
        put("useTemplate", useTemplate)
        put("template", template)
        put("weather", weatherEnabled)
        put("weatherRefresh", weatherRefreshMinutes)
        manualLat?.let { put("lat", it) }
        manualLon?.let { put("lon", it) }
        put("quoteKind", quoteKind.name)
        put("quoteRotate", quoteRotateMinutes)
        put("showDone", showCompletedTasks)
    }

    companion object {
        const val DEFAULT_TEMPLATE = """// ~/today.ts
const today = {
  time: "{{time}}",
  date: "{{date}}",
  weather: "{{weather.temp}}° {{weather.condition}}",
  tasks: {{tasks}},
};
export default today;"""

        fun defaultFields(): List<FieldConfig> = FieldId.entries.map {
            FieldConfig(
                id = it,
                enabled = it in setOf(
                    FieldId.DATE, FieldId.TIME, FieldId.WEATHER, FieldId.TASKS,
                    FieldId.EVENTS, FieldId.BATTERY, FieldId.QUOTE,
                ),
                // Personal data is hidden on the lock screen by default.
                showOnLock = it !in setOf(FieldId.TASKS, FieldId.EVENTS, FieldId.NEXT_EVENT, FieldId.NETWORK, FieldId.NOW_PLAYING),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            runCatching { enumValueOf<E>(name!!) }.getOrDefault(default)

        fun fromJson(json: String?): WallConfig {
            if (json.isNullOrBlank()) return WallConfig()
            return runCatching {
                val o = JSONObject(json)
                val d = WallConfig()
                val parsed = o.optJSONArray("fields")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val f = arr.getJSONObject(i)
                        val id = runCatching { FieldId.valueOf(f.getString("id")) }.getOrNull() ?: return@mapNotNull null
                        FieldConfig(id, f.optBoolean("enabled", false), f.optString("key", id.defaultKey).ifBlank { id.defaultKey }, f.optBoolean("lock", true))
                    }
                }.orEmpty().distinctBy { it.id }
                // Append fields added in newer versions so they appear in settings.
                val missing = d.fields.filter { def -> parsed.none { it.id == def.id } }.map { it.copy(enabled = false) }
                WallConfig(
                    fields = if (parsed.isEmpty()) d.fields else parsed + missing,
                    themeId = o.optString("theme", d.themeId),
                    fontSizeSp = o.optDouble("fontSize", d.fontSizeSp.toDouble()).toFloat(),
                    fontPath = o.optString("fontPath").ifBlank { null },
                    bold = o.optBoolean("bold", d.bold),
                    verticalPosition = o.optDouble("vpos", d.verticalPosition.toDouble()).toFloat(),
                    homeTop = o.optDouble("homeTop", d.homeTop.toDouble()).toFloat(),
                    homeBottom = o.optDouble("homeBottom", d.homeBottom.toDouble()).toFloat(),
                    lockTop = o.optDouble("lockTop", d.lockTop.toDouble()).toFloat(),
                    lockBottom = o.optDouble("lockBottom", d.lockBottom.toDouble()).toFloat(),
                    footerTop = o.optDouble("footerTop", d.footerTop.toDouble()).toFloat(),
                    footerBottom = o.optDouble("footerBottom", d.footerBottom.toDouble()).toFloat(),
                    quotePlacement = enumOr(o.optString("quotePlacement"), d.quotePlacement),
                    compactObjects = o.optBoolean("compact", d.compactObjects),
                    horizontalPaddingDp = o.optInt("hpad", d.horizontalPaddingDp),
                    align = enumOr(o.optString("align"), d.align),
                    lineNumbers = o.optBoolean("lineNumbers", d.lineNumbers),
                    header = o.optString("header", d.header),
                    use24h = o.optBoolean("use24h", d.use24h),
                    datePattern = o.optString("datePattern", d.datePattern),
                    units = enumOr(o.optString("units"), d.units),
                    typingEffect = o.optBoolean("typing", d.typingEffect),
                    blinkingCursor = o.optBoolean("cursor", d.blinkingCursor),
                    flashChanges = o.optBoolean("flash", d.flashChanges),
                    parallax = o.optBoolean("parallax", d.parallax),
                    useTemplate = o.optBoolean("useTemplate", d.useTemplate),
                    template = o.optString("template", d.template),
                    weatherEnabled = o.optBoolean("weather", d.weatherEnabled),
                    weatherRefreshMinutes = o.optInt("weatherRefresh", d.weatherRefreshMinutes).coerceIn(15, 360),
                    manualLat = if (o.has("lat")) o.optDouble("lat") else null,
                    manualLon = if (o.has("lon")) o.optDouble("lon") else null,
                    quoteKind = enumOr(o.optString("quoteKind"), d.quoteKind),
                    quoteRotateMinutes = o.optInt("quoteRotate", d.quoteRotateMinutes).coerceAtLeast(1),
                    showCompletedTasks = o.optBoolean("showDone", d.showCompletedTasks),
                )
            }.getOrDefault(WallConfig())
        }
    }
}
