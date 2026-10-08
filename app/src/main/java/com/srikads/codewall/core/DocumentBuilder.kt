package com.srikads.codewall.core

/** What gets drawn: the main code block and an optional footer (e.g. the quote below the fingerprint). */
data class Rendered(val lines: List<Line>, val footer: List<Line>)

/**
 * Turns the collected field values into the document that is drawn,
 * honoring the user's field order, custom key names and lock-screen visibility.
 */
object DocumentBuilder {
    fun build(config: WallConfig, values: Map<FieldId, JValue>, locked: Boolean): JObject {
        val footerQuote = config.quoteInFooter(locked)
        return JObject(
            config.fields
                .filter { it.enabled && (!locked || it.showOnLock) && !(footerQuote && it.id == FieldId.QUOTE) }
                .map { f ->
                    val v = values[f.id] ?: JNull
                    JEntry(f.key.ifBlank { f.id.defaultKey }, if (config.compactObjects && v is JObject) v.copy(inline = true) else v, id = f.id.name)
                }
        )
    }

    fun render(config: WallConfig, values: Map<FieldId, JValue>, locked: Boolean): Rendered {
        val doc = build(config, values, locked)
        val lines = if (config.useTemplate) TemplateEngine.render(config.template, doc)
        else JsonPrinter().print(doc, config.header)
        val quote = values[FieldId.QUOTE] as? JString
        val showFooter = config.quoteInFooter(locked) && config.isEnabled(FieldId.QUOTE) &&
            (!locked || config.field(FieldId.QUOTE)?.showOnLock == true)
        val footer = if (showFooter && quote != null) listOf(Line("#footer", listOf(Span("// " + quote.value, Tok.COMMENT)))) else emptyList()
        return Rendered(lines, footer)
    }

    fun lines(config: WallConfig, values: Map<FieldId, JValue>, locked: Boolean): List<Line> = render(config, values, locked).lines
}
