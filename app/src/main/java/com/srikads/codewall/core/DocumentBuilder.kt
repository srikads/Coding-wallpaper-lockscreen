package com.srikads.codewall.core

/**
 * Turns the collected field values into the document that is drawn,
 * honoring the user's field order, custom key names and lock-screen visibility.
 */
object DocumentBuilder {
    fun build(config: WallConfig, values: Map<FieldId, JValue>, locked: Boolean): JObject =
        JObject(
            config.fields
                .filter { it.enabled && (!locked || it.showOnLock) }
                .map { f -> JEntry(f.key.ifBlank { f.id.defaultKey }, values[f.id] ?: JNull, id = f.id.name) }
        )

    fun lines(config: WallConfig, values: Map<FieldId, JValue>, locked: Boolean): List<Line> {
        val doc = build(config, values, locked)
        return if (config.useTemplate) TemplateEngine.render(config.template, doc)
        else JsonPrinter().print(doc, config.header)
    }
}
