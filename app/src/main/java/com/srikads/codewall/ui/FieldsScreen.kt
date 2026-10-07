package com.srikads.codewall.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srikads.codewall.core.FieldConfig
import com.srikads.codewall.core.FieldId
import com.srikads.codewall.core.QuoteKind
import com.srikads.codewall.core.Units

@Composable
fun FieldsScreen(state: ConfigState, modifier: Modifier) {
    val cfg = state.config
    var expanded by remember { mutableStateOf<FieldId?>(null) }

    fun updateField(id: FieldId, block: (FieldConfig) -> FieldConfig) =
        state.update { c -> c.copy(fields = c.fields.map { if (it.id == id) block(it) else it }) }

    fun move(index: Int, delta: Int) = state.update { c ->
        val target = index + delta
        if (target !in c.fields.indices) c
        else c.copy(fields = c.fields.toMutableList().apply { add(target, removeAt(index)) })
    }

    LazyColumn(modifier) {
        item {
            Text(
                "Toggle, reorder and rename the JSON keys. The lock icon controls visibility on the lock screen.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        itemsIndexed(cfg.fields, key = { _, f -> f.id }) { i, f ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.clickable { expanded = if (expanded == f.id) null else f.id }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = f.enabled, onCheckedChange = { on -> updateField(f.id) { it.copy(enabled = on) } })
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("\"${f.key}\"", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                            Text(f.id.label + " · " + f.id.description, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { updateField(f.id) { it.copy(showOnLock = !it.showOnLock) } }) {
                            Icon(
                                if (f.showOnLock) Icons.Filled.Lock else Icons.Filled.VisibilityOff,
                                contentDescription = if (f.showOnLock) "Shown on lock screen" else "Hidden on lock screen",
                                tint = if (f.showOnLock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                        Column {
                            IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { move(i, 1) }, enabled = i < cfg.fields.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                        }
                    }
                    if (expanded == f.id) {
                        OutlinedTextField(
                            value = f.key,
                            onValueChange = { v -> updateField(f.id) { it.copy(key = v) } },
                            label = { Text("JSON key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            Section("clock") {
                SwitchRow("24-hour time", cfg.use24h) { v -> state.update { it.copy(use24h = v) } }
                OutlinedTextField(
                    value = cfg.datePattern,
                    onValueChange = { v -> state.update { it.copy(datePattern = v) } },
                    label = { Text("Date pattern") },
                    supportingText = { Text("e.g. yyyy-MM-dd, EEEE d MMMM, dd/MM/yy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Section("weather") {
                SwitchRow("Fetch weather", cfg.weatherEnabled, "Off = CodeWall never uses the network") { v -> state.update { it.copy(weatherEnabled = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Units.entries.forEach { u ->
                        FilterChip(selected = cfg.units == u, onClick = { state.update { it.copy(units = u) } }, label = { Text(u.name.lowercase()) })
                    }
                }
                SliderRow("Refresh every", cfg.weatherRefreshMinutes.toFloat(), 15f..180f, "${cfg.weatherRefreshMinutes} min", steps = 10) { v ->
                    state.update { it.copy(weatherRefreshMinutes = (v / 15).toInt() * 15) }
                }
                Text("Manual coordinates (optional — skips GPS entirely)", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordField("Latitude", cfg.manualLat, Modifier.weight(1f)) { v -> state.update { it.copy(manualLat = v) } }
                    CoordField("Longitude", cfg.manualLon, Modifier.weight(1f)) { v -> state.update { it.copy(manualLon = v) } }
                }
            }
        }
        item {
            Section("tasks") {
                SwitchRow("Show completed tasks", cfg.showCompletedTasks) { v -> state.update { it.copy(showCompletedTasks = v) } }
            }
        }
        item {
            Section("quote") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuoteKind.entries.forEach { k ->
                        FilterChip(selected = cfg.quoteKind == k, onClick = { state.update { it.copy(quoteKind = k) } }, label = { Text(k.name.lowercase()) })
                    }
                }
                SliderRow("Rotate every", cfg.quoteRotateMinutes.toFloat(), 1f..240f, "${cfg.quoteRotateMinutes} min") { v ->
                    state.update { it.copy(quoteRotateMinutes = v.toInt().coerceAtLeast(1)) }
                }
            }
        }
    }
}

@Composable
private fun CoordField(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            if (t.isBlank()) onChange(null) else t.toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
