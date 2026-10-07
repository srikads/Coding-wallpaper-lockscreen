package com.srikads.codewall.core

/**
 * Pretty-prints a [JValue] into syntax-highlighted [Line]s.
 */
class JsonPrinter(private val indentSize: Int = 2) {

    fun print(root: JValue, header: String? = null): List<Line> {
        val out = ArrayList<Line>()
        if (!header.isNullOrBlank()) {
            header.lines().forEachIndexed { i, h -> out += Line("#header$i", listOf(Span(h, Tok.COMMENT))) }
        }
        val first = ArrayList<Span>()
        emit(root, "", 0, first, out, trailingComma = false)
        return out
    }

    /**
     * Appends [value] to the line started in [current] (which may already contain an indent and a key),
     * flushing finished lines into [out].
     */
    private fun emit(
        value: JValue,
        id: String,
        depth: Int,
        current: MutableList<Span>,
        out: MutableList<Line>,
        trailingComma: Boolean,
    ) {
        val comma = if (trailingComma) listOf(Span(",", Tok.PUNCT)) else emptyList()
        when (value) {
            is JObject -> {
                if (value.entries.isEmpty() || value.inline) {
                    current += inlineSpans(value)
                    current += comma
                    out += Line(id.ifEmpty { "#root" }, current.toList(), depth)
                    return
                }
                current += Span("{", Tok.PUNCT)
                out += Line("$id{", current.toList(), depth)
                value.entries.forEachIndexed { i, e ->
                    val childId = "$id/${e.id}"
                    val line = mutableListOf(Span(pad(depth + 1), Tok.PLAIN))
                    line += Span("\"${jsonEscape(e.key)}\"", Tok.KEY)
                    line += Span(": ", Tok.PUNCT)
                    emit(e.value, childId, depth + 1, line, out, i < value.entries.lastIndex)
                }
                out += Line("$id}", listOf(Span(pad(depth), Tok.PLAIN), Span("}", Tok.PUNCT)) + comma, depth)
            }
            is JArray -> {
                if (value.items.isEmpty() || value.inline) {
                    current += inlineSpans(value)
                    current += comma
                    out += Line(id.ifEmpty { "#root" }, current.toList(), depth)
                    return
                }
                current += Span("[", Tok.PUNCT)
                out += Line("$id[", current.toList(), depth)
                value.items.forEachIndexed { i, item ->
                    val line = mutableListOf(Span(pad(depth + 1), Tok.PLAIN))
                    emit(item, "$id/$i", depth + 1, line, out, i < value.items.lastIndex)
                }
                out += Line("$id]", listOf(Span(pad(depth), Tok.PLAIN), Span("]", Tok.PUNCT)) + comma, depth)
            }
            else -> {
                current += inlineSpans(value)
                current += comma
                out += Line(id.ifEmpty { "#root" }, current.toList(), depth)
            }
        }
    }

    private fun pad(depth: Int) = " ".repeat(depth * indentSize)

    /** Single-line, highlighted representation of any value. */
    fun inlineSpans(value: JValue): List<Span> = when (value) {
        is JObject -> if (value.entries.isEmpty()) listOf(Span("{}", Tok.PUNCT)) else buildList {
            add(Span("{ ", Tok.PUNCT))
            value.entries.forEachIndexed { i, e ->
                add(Span("\"${jsonEscape(e.key)}\"", Tok.KEY))
                add(Span(": ", Tok.PUNCT))
                addAll(inlineSpans(e.value))
                if (i < value.entries.lastIndex) add(Span(", ", Tok.PUNCT))
            }
            add(Span(" }", Tok.PUNCT))
        }
        is JArray -> if (value.items.isEmpty()) listOf(Span("[]", Tok.PUNCT)) else buildList {
            add(Span("[", Tok.PUNCT))
            value.items.forEachIndexed { i, v ->
                addAll(inlineSpans(v))
                if (i < value.items.lastIndex) add(Span(", ", Tok.PUNCT))
            }
            add(Span("]", Tok.PUNCT))
        }
        is JString -> listOf(Span("\"${jsonEscape(value.value)}\"", Tok.STRING))
        is JNumber -> listOf(Span(value.raw, Tok.NUMBER))
        is JBool -> listOf(Span(value.value.toString(), Tok.BOOL))
        JNull -> listOf(Span("null", Tok.NULL))
    }
}
