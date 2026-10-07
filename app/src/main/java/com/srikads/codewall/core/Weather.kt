package com.srikads.codewall.core

/** WMO weather interpretation codes used by Open-Meteo. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1 -> "mostly_clear"
        2 -> "partly_cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing_drizzle"
        61 -> "light_rain"
        63 -> "rain"
        65 -> "heavy_rain"
        66, 67 -> "freezing_rain"
        71 -> "light_snow"
        73 -> "snow"
        75 -> "heavy_snow"
        77 -> "snow_grains"
        80, 81, 82 -> "showers"
        85, 86 -> "snow_showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm_hail"
        else -> "unknown"
    }
}

/** Rounds a coordinate to [decimals] places (1 decimal is roughly 11 km) before it leaves the device. */
fun roundCoordinate(value: Double, decimals: Int = 1): Double {
    val f = Math.pow(10.0, decimals.toDouble())
    return Math.round(value * f) / f
}
