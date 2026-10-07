package com.srikads.codewall.core

/** "2h 05m", "45m", "3d 4h" style durations. */
fun humanDuration(millis: Long): String {
    val totalMin = (millis.coerceAtLeast(0) / 60_000)
    val d = totalMin / (60 * 24)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        else -> "${m}m"
    }
}
