package com.srikads.codewall.core

/**
 * Minimal ordered JSON tree used to describe what the wallpaper shows.
 * Kept free of Android dependencies so it can be unit-tested on the JVM.
 */
sealed interface JValue

data class JEntry(val key: String, val value: JValue, val id: String = key)

data class JObject(val entries: List<JEntry>, val inline: Boolean = false) : JValue {
    operator fun get(key: String): JValue? = entries.firstOrNull { it.key == key }?.value
}

data class JArray(val items: List<JValue>, val inline: Boolean = false) : JValue
data class JString(val value: String) : JValue
data class JNumber(val raw: String) : JValue {
    constructor(value: Int) : this(value.toString())
    constructor(value: Long) : this(value.toString())
    constructor(value: Double, decimals: Int = 1) : this(formatDecimal(value, decimals))
}
data class JBool(val value: Boolean) : JValue
data object JNull : JValue

fun obj(vararg pairs: Pair<String, JValue?>, inline: Boolean = false): JObject =
    JObject(pairs.mapNotNull { (k, v) -> v?.let { JEntry(k, it) } }, inline)

fun str(value: String?): JValue = value?.let { JString(it) } ?: JNull

internal fun formatDecimal(value: Double, decimals: Int): String {
    if (decimals <= 0) return Math.round(value).toString()
    val s = String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
    return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/** Escapes a string the way JSON requires. */
fun jsonEscape(s: String): String = buildString(s.length + 2) {
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code))
        else -> append(c)
    }
}

/** Compact single-line JSON text of a value. */
fun JValue.toJsonText(): String = when (this) {
    is JObject -> entries.joinToString(", ", "{ ", " }") { "\"${jsonEscape(it.key)}\": ${it.value.toJsonText()}" }
        .let { if (entries.isEmpty()) "{}" else it }
    is JArray -> if (items.isEmpty()) "[]" else items.joinToString(", ", "[", "]") { it.toJsonText() }
    is JString -> "\"${jsonEscape(value)}\""
    is JNumber -> raw
    is JBool -> value.toString()
    JNull -> "null"
}

/**
 * Flattens a tree into dotted paths, e.g. `weather.temp -> 21.4`.
 * Strings are returned raw (no quotes); everything else as JSON text.
 * Containers are also emitted under their own path as JSON text.
 */
fun JValue.flatten(prefix: String = "", out: MutableMap<String, String> = linkedMapOf()): Map<String, String> {
    when (this) {
        is JObject -> {
            if (prefix.isNotEmpty()) out[prefix] = toJsonText()
            entries.forEach { it.value.flatten(if (prefix.isEmpty()) it.key else "$prefix.${it.key}", out) }
        }
        is JArray -> {
            if (prefix.isNotEmpty()) out[prefix] = toJsonText()
            items.forEachIndexed { i, v -> v.flatten("$prefix.$i", out) }
        }
        is JString -> out[prefix] = value
        else -> out[prefix] = toJsonText()
    }
    return out
}
