package com.srikads.codewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.srikads.codewall.config.ConfigStore
import com.srikads.codewall.core.TaskItem
import java.time.LocalDate

private enum class When(val label: String) { TODAY("Today"), TOMORROW("Tomorrow"), UNTIL_DONE("Until done"), DAILY("Every day") }

@Composable
fun TasksScreen(store: ConfigStore, modifier: Modifier) {
    var tasks by remember { mutableStateOf(store.tasks()) }
    var title by remember { mutableStateOf("") }
    var whenSel by remember { mutableStateOf(When.TODAY) }
    val today = LocalDate.now().toString()

    fun save(new: List<TaskItem>) {
        tasks = new
        store.saveTasks(new)
    }

    fun add() {
        val t = title.trim()
        if (t.isEmpty()) return
        val item = TaskItem(
            id = System.currentTimeMillis(),
            title = t,
            date = when (whenSel) {
                When.TODAY -> today
                When.TOMORROW -> LocalDate.now().plusDays(1).toString()
                else -> null
            },
            daily = whenSel == When.DAILY,
        )
        save(tasks + item)
        title = ""
    }

    LazyColumn(modifier) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("New task") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, "Add") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    When.entries.forEach { w -> FilterChip(selected = whenSel == w, onClick = { whenSel = w }, label = { Text(w.label) }) }
                }
                Text(
                    "Tasks are stored only on this device. Calendar events show up automatically under \"events\" once calendar access is granted.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(tasks.sortedWith(compareBy<TaskItem>({ it.isDone(today) }, { it.date ?: "" })), key = { it.id }) { task ->
            val done = task.isDone(today)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { checked ->
                    save(tasks.map { if (it.id == task.id) it.copy(doneOn = if (checked) today else null) else it })
                })
                Column(Modifier.weight(1f)) {
                    Text(task.title, textDecoration = if (done) TextDecoration.LineThrough else null)
                    val date = task.date
                    val meta = when {
                        task.daily -> "every day"
                        date == null -> "until done"
                        date < today -> "overdue · $date"
                        date == today -> "today"
                        else -> date
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                IconButton(onClick = { save(tasks.filterNot { it.id == task.id }) }) { Icon(Icons.Filled.Delete, "Delete") }
            }
        }
        if (tasks.any { !it.daily && it.doneOn != null && it.doneOn < today }) {
            item {
                TextButton(onClick = { save(tasks.filterNot { !it.daily && it.doneOn != null && it.doneOn < today }) }, modifier = Modifier.padding(8.dp)) {
                    Text("Clear old completed tasks")
                }
            }
        }
    }
}
