package com.srikads.codewall.core

/** Editor-style color palette. Colors are ARGB ints. */
data class CodeTheme(
    val id: String,
    val name: String,
    val background: Int,
    val plain: Int,
    val key: Int,
    val string: Int,
    val number: Int,
    val bool: Int,
    val nul: Int,
    val punct: Int,
    val comment: Int,
    val keyword: Int,
    val lineNumber: Int,
    val cursor: Int,
    val highlight: Int,
) {
    fun color(tok: Tok): Int = when (tok) {
        Tok.KEY -> key
        Tok.STRING -> string
        Tok.NUMBER -> number
        Tok.BOOL -> bool
        Tok.NULL -> nul
        Tok.PUNCT -> punct
        Tok.COMMENT -> comment
        Tok.KEYWORD -> keyword
        Tok.PLAIN -> plain
    }

    companion object {
        private fun c(hex: Long): Int = hex.toInt()

        val DRACULA = CodeTheme(
            "dracula", "Dracula",
            background = c(0xFF282A36), plain = c(0xFFF8F8F2), key = c(0xFF8BE9FD), string = c(0xFFF1FA8C),
            number = c(0xFFBD93F9), bool = c(0xFFFF79C6), nul = c(0xFFFF79C6), punct = c(0xFFF8F8F2),
            comment = c(0xFF6272A4), keyword = c(0xFFFF79C6), lineNumber = c(0xFF44475A),
            cursor = c(0xFFF8F8F2), highlight = c(0x5544475A),
        )
        val MONOKAI = CodeTheme(
            "monokai", "Monokai",
            background = c(0xFF272822), plain = c(0xFFF8F8F2), key = c(0xFFF92672), string = c(0xFFE6DB74),
            number = c(0xFFAE81FF), bool = c(0xFFAE81FF), nul = c(0xFFAE81FF), punct = c(0xFFF8F8F2),
            comment = c(0xFF75715E), keyword = c(0xFF66D9EF), lineNumber = c(0xFF49483E),
            cursor = c(0xFFF8F8F0), highlight = c(0x5549483E),
        )
        val ONE_DARK = CodeTheme(
            "one_dark", "One Dark",
            background = c(0xFF282C34), plain = c(0xFFABB2BF), key = c(0xFFE06C75), string = c(0xFF98C379),
            number = c(0xFFD19A66), bool = c(0xFFD19A66), nul = c(0xFFC678DD), punct = c(0xFFABB2BF),
            comment = c(0xFF5C6370), keyword = c(0xFFC678DD), lineNumber = c(0xFF4B5263),
            cursor = c(0xFF528BFF), highlight = c(0x553E4451),
        )
        val MATRIX = CodeTheme(
            "matrix", "Matrix",
            background = c(0xFF000000), plain = c(0xFF00FF41), key = c(0xFF00FF41), string = c(0xFF7CFF9B),
            number = c(0xFFB6FFC4), bool = c(0xFFB6FFC4), nul = c(0xFF008F11), punct = c(0xFF008F11),
            comment = c(0xFF006B0D), keyword = c(0xFF00FF41), lineNumber = c(0xFF003B00),
            cursor = c(0xFF00FF41), highlight = c(0x33003B00),
        )
        val AMOLED = CodeTheme(
            "amoled", "AMOLED Black",
            background = c(0xFF000000), plain = c(0xFFD0D0D0), key = c(0xFF82AAFF), string = c(0xFFC3E88D),
            number = c(0xFFF78C6C), bool = c(0xFFFF5370), nul = c(0xFFFF5370), punct = c(0xFF89DDFF),
            comment = c(0xFF545454), keyword = c(0xFFC792EA), lineNumber = c(0xFF2A2A2A),
            cursor = c(0xFFFFCC00), highlight = c(0x33FFFFFF),
        )

        val ALL = listOf(DRACULA, MONOKAI, ONE_DARK, MATRIX, AMOLED)

        fun byId(id: String?): CodeTheme = ALL.firstOrNull { it.id == id } ?: DRACULA
    }
}
