package com.srikads.codewall.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srikads.codewall.core.Align
import com.srikads.codewall.core.CodeTheme
import com.srikads.codewall.core.FieldId
import com.srikads.codewall.core.QuotePlacement
import com.srikads.codewall.core.WallConfig
import java.io.File

@Composable
fun StyleScreen(state: ConfigState, modifier: Modifier) {
    val cfg = state.config
    val context = LocalContext.current
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val path = importFont(context, uri)
            if (path != null) state.update { it.copy(fontPath = path) }
            else Toast.makeText(context, "That file isn't a usable font", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Section("theme") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CodeTheme.ALL.forEach { t -> ThemeSwatch(t, selected = t.id == cfg.themeId) { state.update { it.copy(themeId = t.id) } } }
            }
        }

        Section("font") {
            SliderRow("Size", cfg.fontSizeSp, 9f..28f, "%.0f sp".format(cfg.fontSizeSp)) { v -> state.update { it.copy(fontSizeSp = v) } }
            SwitchRow("Bold", cfg.bold) { v -> state.update { it.copy(bold = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cfg.fontPath?.let { "Custom: " + File(it).name.substringAfter('-') } ?: "System monospace", Modifier.weight(1f))
                if (cfg.fontPath != null) TextButton(onClick = {
                    cfg.fontPath?.let { File(it).delete() }
                    state.update { it.copy(fontPath = null) }
                }) { Text("Reset") }
                OutlinedButton(onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) }) {
                    Text("Import .ttf")
                }
            }
            Text("Tip: JetBrains Mono, Fira Code or Cascadia Code look great. The font is copied into private app storage.", style = MaterialTheme.typography.bodySmall)
        }

        Section("screen layout") {
            Text(
                "Dashed boxes in the preview show these areas. Switch the preview to \"Lock screen\" to line the code up " +
                    "between the system clock and the fingerprint icon.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Lock screen code area", style = MaterialTheme.typography.labelLarge)
            RangeRow("From", cfg.lockTop) { v -> state.update { it.copy(lockTop = v.coerceAtMost(it.lockBottom - 0.05f)) } }
            RangeRow("To", cfg.lockBottom) { v -> state.update { it.copy(lockBottom = v.coerceAtLeast(it.lockTop + 0.05f)) } }
            Text("Home screen code area", style = MaterialTheme.typography.labelLarge)
            RangeRow("From", cfg.homeTop) { v -> state.update { it.copy(homeTop = v.coerceAtMost(it.homeBottom - 0.05f)) } }
            RangeRow("To", cfg.homeBottom) { v -> state.update { it.copy(homeBottom = v.coerceAtLeast(it.homeTop + 0.05f)) } }
            Text("Quote placement", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    QuotePlacement.INLINE to "in code",
                    QuotePlacement.FOOTER_ON_LOCK to "footer on lock",
                    QuotePlacement.FOOTER_ALWAYS to "footer always",
                ).forEach { (p, label) ->
                    FilterChip(selected = cfg.quotePlacement == p, onClick = { state.update { it.copy(quotePlacement = p) } }, label = { Text(label) })
                }
            }
            if (cfg.quotePlacement != QuotePlacement.INLINE) {
                Text("Footer area (below the fingerprint icon)", style = MaterialTheme.typography.labelLarge)
                RangeRow("From", cfg.footerTop) { v -> state.update { it.copy(footerTop = v.coerceAtMost(it.footerBottom - 0.02f)) } }
                RangeRow("To", cfg.footerBottom) { v -> state.update { it.copy(footerBottom = v.coerceAtLeast(it.footerTop + 0.02f)) } }
            }
            SwitchRow("Compact objects", cfg.compactObjects, "Put weather, device, … on one wrapped line so more fits") { v -> state.update { it.copy(compactObjects = v) } }
            TextButton(onClick = {
                state.update {
                    val d = WallConfig()
                    it.copy(homeTop = d.homeTop, homeBottom = d.homeBottom, lockTop = d.lockTop, lockBottom = d.lockBottom, footerTop = d.footerTop, footerBottom = d.footerBottom)
                }
            }) { Text("Reset areas (Pixel defaults)") }
        }

        Section("layout") {
            SliderRow("Position inside area", cfg.verticalPosition, 0f..1f, "${(cfg.verticalPosition * 100).toInt()}%") { v -> state.update { it.copy(verticalPosition = v) } }
            SliderRow("Side padding", cfg.horizontalPaddingDp.toFloat(), 0f..64f, "${cfg.horizontalPaddingDp} dp") { v -> state.update { it.copy(horizontalPaddingDp = v.toInt()) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Align.entries.forEach { a ->
                    FilterChip(selected = cfg.align == a, onClick = { state.update { it.copy(align = a) } }, label = { Text(a.name.lowercase()) })
                }
            }
            SwitchRow("Line numbers", cfg.lineNumbers) { v -> state.update { it.copy(lineNumbers = v) } }
            OutlinedTextField(
                value = cfg.header,
                onValueChange = { v -> state.update { it.copy(header = v) } },
                label = { Text("Header comment") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("animations") {
            SwitchRow("Typing effect", cfg.typingEffect, "Code types itself in when the screen turns on") { v -> state.update { it.copy(typingEffect = v) } }
            SwitchRow("Blinking cursor", cfg.blinkingCursor) { v -> state.update { it.copy(blinkingCursor = v) } }
            SwitchRow("Highlight changes", cfg.flashChanges, "Flash a line when its value changes") { v -> state.update { it.copy(flashChanges = v) } }
            SwitchRow("Parallax", cfg.parallax, "Shift slightly when swiping home screens") { v -> state.update { it.copy(parallax = v) } }
        }

        Section("template") {
            SwitchRow("Use custom template", cfg.useTemplate, "Write any code shape with {{placeholders}}") { v -> state.update { it.copy(useTemplate = v) } }
            if (cfg.useTemplate) {
                OutlinedTextField(
                    value = cfg.template,
                    onValueChange = { v -> state.update { it.copy(template = v) } },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
                Row {
                    TextButton(onClick = { state.update { it.copy(template = WallConfig.DEFAULT_TEMPLATE) } }) { Text("Reset template") }
                }
                Text("Available placeholders:", style = MaterialTheme.typography.labelMedium)
                Text(placeholderHelp(cfg), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RangeRow(label: String, value: Float, onChange: (Float) -> Unit) =
    SliderRow(label, value, 0f..1f, "${(value * 100).toInt()}% of screen") { onChange((it * 100).toInt() / 100f) }

@Composable
private fun ThemeSwatch(theme: CodeTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 84.dp, height = 56.dp)
                .background(Color(theme.background), shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF), shape)
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(20.dp, 6.dp).background(Color(theme.key), RoundedCornerShape(2.dp)))
                    Box(Modifier.size(30.dp, 6.dp).background(Color(theme.string), RoundedCornerShape(2.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(14.dp, 6.dp).background(Color(theme.key), RoundedCornerShape(2.dp)))
                    Box(Modifier.size(16.dp, 6.dp).background(Color(theme.number), RoundedCornerShape(2.dp)))
                }
                Box(Modifier.size(40.dp, 6.dp).background(Color(theme.comment), RoundedCornerShape(2.dp)))
            }
        }
        Text(theme.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun placeholderHelp(cfg: WallConfig): String {
    val sub = mapOf(
        FieldId.WEATHER to listOf("temp", "feels_like", "unit", "condition", "high", "low", "humidity", "wind", "rain_chance", "sunrise", "sunset"),
        FieldId.BATTERY to listOf("level", "charging", "temp_c"),
        FieldId.DEVICE to listOf("ram", "storage_free", "uptime"),
        FieldId.ALARM to listOf("at", "in"),
        FieldId.NEXT_EVENT to listOf("title", "in"),
        FieldId.NOW_PLAYING to listOf("title", "artist", "playing"),
        FieldId.NETWORK to listOf("type", "vpn", "ip"),
        FieldId.TASKS to listOf("0.title", "0.done"),
        FieldId.EVENTS to listOf("0"),
    )
    return cfg.fields.filter { it.enabled }.joinToString("\n") { f ->
        (listOf("{{${f.key}}}") + sub[f.id].orEmpty().map { "{{${f.key}.$it}}" }).joinToString("  ")
    }.ifEmpty { "(enable some fields first)" }
}

/** Copies a picked font into private storage and verifies it loads. */
private fun importFont(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    val out = File(dir, "${System.currentTimeMillis()}-custom.ttf")
    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
    Typeface.createFromFile(out) // throws on invalid files
    dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
    out.absolutePath
}.getOrNull()
