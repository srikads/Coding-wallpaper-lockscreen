package com.srikads.codewall.core

/**
 * Renders a user-written template such as
 *
 * ```
 * const today = {
 *   time: "{{time}}",
 *   temp: {{weather.temp}},
 * };
 * ```
 *
 * Placeholders are `{{path}}` where path is a dotted path into the document
 * (see [flatten]). Unknown placeholders render as `null`. The result is
 * tokenized with a small, language-agnostic highlighter.
 */
object TemplateEngine {
    private val placeholder = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*}}""")

    fun fill(template: String, doc: JValue): String {
        val values = doc.flatten()
        return placeholder.replace(template) { m -> values[m.groupValues[1]] ?: "null" }
    }

    /** Lists every placeholder path available for [doc], for the settings help text. */
    fun availablePaths(doc: JValue): List<String> = doc.flatten().keys.toList()

    fun render(template: String, doc: JValue): List<Line> =
        fill(template, doc).lines().mapIndexed { i, l -> Line("#t$i", Highlighter.tokenize(l)) }
}

/** Tiny highlighter for JSON / JS / Kotlin-ish single lines. */
object Highlighter {
    private val keywords = setOf(
        "const", "let", "var", "val", "fun", "function", "return", "if", "else", "for", "while",
        "class", "object", "import", "export", "def", "true", "false", "null", "undefined", "new",
        "this", "data", "type", "interface", "when", "in", "is", "public", "private", "static",
    )

    fun tokenize(line: String): List<Span> {
        val spans = ArrayList<Span>()
        var i = 0
        val n = line.length
        fun add(text: String, tok: Tok) {
            if (text.isEmpty()) return
            val last = spans.lastOrNull()
            if (last != null && last.tok == tok) spans[spans.lastIndex] = Span(last.text + text, tok)
            else spans += Span(text, tok)
        }
        while (i < n) {
            val c = line[i]
            when {
                c == '/' && i + 1 < n && line[i + 1] == '/' -> { add(line.substring(i), Tok.COMMENT); i = n }
                c == '#' && line.substring(0, i).isBlank() -> { add(line.substring(i), Tok.COMMENT); i = n }
                c == '"' || c == '\'' || c == '`' -> {
                    var j = i + 1
                    while (j < n && line[j] != c) { if (line[j] == '\\') j++; j++ }
                    j = minOf(j + 1, n)
                    val text = line.substring(i, j)
                    var k = j
                    while (k < n && line[k] == ' ') k++
                    add(text, if (k < n && line[k] == ':') Tok.KEY else Tok.STRING)
                    i = j
                }
                c.isDigit() || (c == '-' && i + 1 < n && line[i + 1].isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit())) -> {
                    var j = i + 1
                    while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == 'e' || line[j] == 'E')) j++
                    add(line.substring(i, j), Tok.NUMBER); i = j
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    var j = i + 1
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '$')) j++
                    val word = line.substring(i, j)
                    var k = j
                    while (k < n && line[k] == ' ') k++
                    val tok = when {
                        word == "true" || word == "false" -> Tok.BOOL
                        word == "null" || word == "undefined" -> Tok.NULL
                        word in keywords -> Tok.KEYWORD
                        k < n && line[k] == ':' && (k + 1 >= n || line[k + 1] != ':') -> Tok.KEY
                        else -> Tok.PLAIN
                    }
                    add(word, tok); i = j
                }
                c in "{}[](),:;=<>+*.!?|&" -> { add(c.toString(), Tok.PUNCT); i++ }
                else -> { add(c.toString(), Tok.PLAIN); i++ }
            }
        }
        return spans
    }
}
