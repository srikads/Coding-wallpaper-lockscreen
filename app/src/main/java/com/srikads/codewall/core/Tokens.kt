package com.srikads.codewall.core

enum class Tok { KEY, STRING, NUMBER, BOOL, NULL, PUNCT, COMMENT, KEYWORD, PLAIN }

data class Span(val text: String, val tok: Tok)

/** One rendered line. [id] is a stable path used to detect value changes between frames. */
data class Line(val id: String, val spans: List<Span>, val indent: Int = 0) {
    val text: String by lazy { spans.joinToString("") { it.text } }
    val length: Int get() = text.length
}
