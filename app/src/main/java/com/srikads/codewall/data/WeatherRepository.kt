package com.srikads.codewall.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import com.srikads.codewall.core.JNumber
import com.srikads.codewall.core.JString
import com.srikads.codewall.core.JValue
import com.srikads.codewall.core.Units
import com.srikads.codewall.core.WallConfig
import com.srikads.codewall.core.WeatherCodes
import com.srikads.codewall.core.obj
import com.srikads.codewall.core.roundCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Fetches weather from Open-Meteo (no API key, no account).
 *
 * Privacy: the only data that leaves the device is a latitude/longitude rounded to
 * one decimal (~11 km) and the unit preference. The response is cached locally.
 */
class WeatherRepository(context: Context) {
    private val app = context.applicationContext
    private val cache = app.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    @Volatile private var lastAttempt = 0L

    /** Returns the cached weather, refreshing it first if it is older than the configured interval. */
    suspend fun get(config: WallConfig, force: Boolean = false): JValue? = withContext(Dispatchers.IO) {
        if (!config.weatherEnabled) return@withContext null
        val now = System.currentTimeMillis()
        val fetchedAt = cache.getLong("at", 0L)
        val sameUnits = cache.getString("units", null) == config.units.name
        val stale = !sameUnits || now - fetchedAt > config.weatherRefreshMinutes * 60_000L
        // Back off for 5 minutes after any attempt so failures don't hammer the network.
        if ((stale || force) && now - lastAttempt > 5 * 60_000L) {
            lastAttempt = now
            runCatching { fetch(config) }.getOrNull()?.let { body ->
                cache.edit().putString("body", body).putLong("at", now).putString("units", config.units.name).apply()
            }
        }
        cache.getString("body", null)?.let { parse(it, config.units) }
    }

    private suspend fun fetch(config: WallConfig): String? {
        val (lat, lon) = coordinates(config) ?: return null
        val imperial = config.units == Units.IMPERIAL
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(roundCoordinate(lat))
            append("&longitude=").append(roundCoordinate(lon))
            append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day")
            append("&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset")
            append("&timezone=auto&forecast_days=1")
            if (imperial) append("&temperature_unit=fahrenheit&wind_speed_unit=mph")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "CodeWall")
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun coordinates(config: WallConfig): Pair<Double, Double>? {
        if (config.manualLat != null && config.manualLon != null) return config.manualLat to config.manualLon
        if (!Permissions.location(app)) return null
        return currentLocation()?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission") // checked by caller
    private suspend fun currentLocation(): Location? {
        val lm = app.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < 3 * 3_600_000L) return recent
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return recent
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, app.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, { if (cont.isActive) cont.resume(it) }, Looper.getMainLooper())
                }
            }
        }
        return fresh ?: recent
    }

    private fun parse(body: String, units: Units): JValue? = runCatching {
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val daily = o.optJSONObject("daily")
        fun dailyNum(name: String) = daily?.optJSONArray(name)?.optDouble(0)?.takeUnless { it.isNaN() }
        fun dailyTime(name: String) = daily?.optJSONArray(name)?.optString(0)?.substringAfter('T')?.ifBlank { null }
        val unit = if (units == Units.IMPERIAL) "°F" else "°C"
        obj(
            "temp" to JNumber(cur.getDouble("temperature_2m")),
            "feels_like" to JNumber(cur.optDouble("apparent_temperature")).takeUnless { cur.isNull("apparent_temperature") },
            "unit" to JString(unit),
            "condition" to JString(WeatherCodes.describe(cur.optInt("weather_code", -1))),
            "high" to dailyNum("temperature_2m_max")?.let { JNumber(it) },
            "low" to dailyNum("temperature_2m_min")?.let { JNumber(it) },
            "humidity" to JNumber(cur.optInt("relative_humidity_2m")),
            "wind" to JString("${Math.round(cur.optDouble("wind_speed_10m"))} ${if (units == Units.IMPERIAL) "mph" else "km/h"}"),
            "rain_chance" to dailyNum("precipitation_probability_max")?.let { JNumber(it.toInt()) },
            "sunrise" to dailyTime("sunrise")?.let { JString(it) },
            "sunset" to dailyTime("sunset")?.let { JString(it) },
        )
    }.getOrNull()
}
