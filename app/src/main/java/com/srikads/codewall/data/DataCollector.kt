package com.srikads.codewall.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.srikads.codewall.config.ConfigStore
import com.srikads.codewall.core.FieldId
import com.srikads.codewall.core.JArray
import com.srikads.codewall.core.JBool
import com.srikads.codewall.core.JNull
import com.srikads.codewall.core.JNumber
import com.srikads.codewall.core.JString
import com.srikads.codewall.core.JValue
import com.srikads.codewall.core.Quotes
import com.srikads.codewall.core.TaskItem
import com.srikads.codewall.core.WallConfig
import com.srikads.codewall.core.humanDuration
import com.srikads.codewall.core.obj
import com.srikads.codewall.core.str
import java.net.Inet4Address
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads everything the wallpaper can show. All sources are on-device except weather.
 * Every method degrades to a hint string (e.g. "grant calendar access") instead of failing.
 */
class DataCollector(context: Context) {
    private val app = context.applicationContext
    val store = ConfigStore(app)
    val weather = WeatherRepository(app)
    val steps = StepCounter(app)

    private data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

    // ---- fast (every frame) ----

    fun clock(config: WallConfig, nowMs: Long): Map<FieldId, JValue> {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val timeFmt = if (config.use24h) TIME_24 else TIME_12
        val date = runCatching { DateTimeFormatter.ofPattern(config.datePattern, Locale.getDefault()).format(now) }
            .getOrElse { DateTimeFormatter.ISO_LOCAL_DATE.format(now) }
        return mapOf(FieldId.TIME to JString(timeFmt.format(now)), FieldId.DATE to JString(date))
    }

    // ---- slow (every ~15 s, off the main thread) ----

    fun slow(config: WallConfig, nowMs: Long): Map<FieldId, JValue> {
        val out = HashMap<FieldId, JValue>()
        fun put(id: FieldId, block: () -> JValue?) {
            if (config.isEnabled(id)) out[id] = runCatching(block).getOrNull() ?: JNull
        }
        val today = LocalDate.now().toString()
        val events by lazy { todayEvents(nowMs) }
        put(FieldId.TASKS) { TaskItem.todayValue(store.tasks(), today, config.showCompletedTasks) }
        put(FieldId.EVENTS) { events?.let { list -> JArray(list.map { JString(formatEvent(it, config)) }) } ?: JString(HINT_CALENDAR) }
        put(FieldId.NEXT_EVENT) { events?.let { nextEvent(it, nowMs) } ?: JString(HINT_CALENDAR) }
        put(FieldId.ALARM) { alarm(config, nowMs) }
        put(FieldId.BATTERY) { battery() }
        put(FieldId.DEVICE) { device() }
        put(FieldId.STEPS) { stepsValue() }
        put(FieldId.NOW_PLAYING) { nowPlaying() }
        put(FieldId.NETWORK) { network() }
        put(FieldId.QUOTE) { JString(Quotes.pick(config.quoteKind, nowMs, config.quoteRotateMinutes)) }
        return out
    }

    @SuppressLint("MissingPermission")
    private fun todayEvents(nowMs: Long): List<Event>? {
        if (!Permissions.calendar(app)) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = ArrayList<Event>()
        app.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                events += Event(c.getString(0) ?: "(untitled)", c.getLong(1), c.getLong(2), c.getInt(3) == 1)
            }
        }
        // All-day instances are stored as UTC midnights; keep only the ones dated today.
        val today = LocalDate.now(zone)
        return events.filter { e ->
            if (e.allDay) Instant.ofEpochMilli(e.begin).atZone(ZoneId.of("UTC")).toLocalDate() == today
            else e.end > nowMs - 3_600_000L // keep events that ended within the last hour
        }
    }

    private fun formatEvent(e: Event, config: WallConfig): String {
        if (e.allDay) return "all-day ${e.title}"
        val fmt = if (config.use24h) HM_24 else HM_12
        return "${fmt.format(Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()))} ${e.title}"
    }

    private fun nextEvent(events: List<Event>, nowMs: Long): JValue {
        val ongoing = events.firstOrNull { !it.allDay && it.begin <= nowMs && it.end > nowMs }
        val next = events.firstOrNull { !it.allDay && it.begin > nowMs }
        return when {
            ongoing != null -> obj("title" to JString(ongoing.title), "ends_in" to JString(humanDuration(ongoing.end - nowMs)), inline = true)
            next != null -> obj("title" to JString(next.title), "in" to JString(humanDuration(next.begin - nowMs)), inline = true)
            else -> JNull
        }
    }

    private fun alarm(config: WallConfig, nowMs: Long): JValue {
        val am = app.getSystemService(AlarmManager::class.java) ?: return JNull
        val info = am.nextAlarmClock ?: return JNull
        val at = Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault())
        val fmt = if (config.use24h) HM_24 else HM_12
        return obj(
            "at" to JString("${fmt.format(at)} ${DAY.format(at)}"),
            "in" to JString(humanDuration(info.triggerTime - nowMs)),
            inline = true,
        )
    }

    private fun battery(): JValue {
        val i = ContextCompat.registerReceiver(app, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) ?: return JNull
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return obj(
            "level" to JNumber(level * 100 / scale),
            "charging" to JBool(charging),
            "temp_c" to if (temp == Int.MIN_VALUE) null else JNumber(temp / 10.0),
            inline = true,
        )
    }

    private fun device(): JValue {
        val am = app.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val gb = 1024.0 * 1024 * 1024
        val stat = StatFs(Environment.getDataDirectory().path)
        return obj(
            "ram" to JString("%.1f/%.1f GB".format(Locale.ROOT, (mem.totalMem - mem.availMem) / gb, mem.totalMem / gb)),
            "storage_free" to JString("%.0f GB".format(Locale.ROOT, stat.availableBytes / gb)),
            "uptime" to JString(humanDuration(SystemClock.elapsedRealtime())),
        )
    }

    private fun stepsValue(): JValue = when {
        !steps.available -> JString("no step sensor")
        !Permissions.activity(app) -> JString("grant activity access")
        else -> steps.today?.let { JNumber(it) } ?: JNumber(0)
    }

    @SuppressLint("MissingPermission") // access is granted via the notification listener
    private fun nowPlaying(): JValue {
        if (!Permissions.notificationListener(app)) return JString("enable notification access")
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return JNull
        val controllers = msm.getActiveSessions(ComponentName(app, MediaListenerService::class.java))
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull() ?: return JNull
        val md = c.metadata ?: return JNull
        return obj(
            "title" to str(md.getString(MediaMetadata.METADATA_KEY_TITLE)),
            "artist" to str(md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)),
            "playing" to JBool(c.playbackState?.state == PlaybackState.STATE_PLAYING),
        )
    }

    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is a normal permission
    private fun network(): JValue {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return JNull
        val net = cm.activeNetwork ?: return obj("type" to JString("offline"), inline = true)
        val caps = cm.getNetworkCapabilities(net)
        val type = when {
            caps == null -> "unknown"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val ip = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        return obj(
            "type" to JString(type),
            "vpn" to JBool(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true),
            "ip" to str(ip),
            inline = true,
        )
    }

    companion object {
        const val HINT_CALENDAR = "grant calendar access"
        private val TIME_24 = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val TIME_12 = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.US)
        private val HM_24 = DateTimeFormatter.ofPattern("HH:mm")
        private val HM_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val DAY = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    }
}
