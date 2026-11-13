package com.example.niaspoproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.niaspoproject.ui.theme.NiaspoProjectTheme
import java.io.IOException
import kotlinx.coroutines.launch
import org.json.JSONException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiaspoProjectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TasksScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<TaskEditor?>(null) }
    var deleting by remember { mutableStateOf<Task?>(null) }

    fun runRequest(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: IOException) {
                error = "Запрос не выполнен. Проверьте подключение и обновите список."
            } catch (e: JSONException) {
                error = "Сервер вернул данные в неожиданном формате."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { runRequest { tasks = TaskApi.list() } }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp).testTag("tasksList"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Задачи", style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { editor = TaskEditor() }) { Text("Добавить") }
                TextButton(enabled = !busy, onClick = { runRequest { tasks = TaskApi.list() } }) {
                    Text("Обновить")
                }
            }
            if (busy) Text("Выполняется запрос…")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!busy && error == null && tasks.isEmpty()) Text("Задач пока нет")
        }
        items(tasks, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.completed,
                            enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Выполнено: ${task.title}" },
                            onCheckedChange = { completed ->
                                runRequest {
                                    val updated = TaskApi.update(task.copy(completed = completed))
                                    tasks = tasks.map { if (it.id == updated.id) updated else it }
                                }
                            }
                        )
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Row {
                        TextButton(
                            enabled = !busy,
                            modifier = Modifier.testTag("edit:${task.title}"),
                            onClick = { editor = TaskEditor(task, task.title) }
                        ) {
                            Text("Изменить")
                        }
                        TextButton(
                            enabled = !busy,
                            modifier = Modifier.testTag("delete:${task.title}"),
                            onClick = { deleting = task }
                        ) { Text("Удалить") }
                    }
                }
            }
        }
    }

    editor?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!busy) editor = null },
            title = { Text(if (draft.task == null) "Новая задача" else "Редактирование задачи") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { editor = draft.copy(title = it) },
                        enabled = !busy,
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.testTag("taskTitle")
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && TaskTitle.isValid(draft.title),
                    onClick = {
                        runRequest {
                            val title = TaskTitle.normalize(draft.title)
                            val existing = draft.task
                            tasks = if (existing == null) {
                                (tasks + TaskApi.create(title)).sortedBy { it.id }
                            } else {
                                val updated = TaskApi.update(existing.copy(title = title))
                                tasks.map { if (it.id == updated.id) updated else it }
                            }
                            editor = null
                        }
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { editor = null }) { Text("Отмена") } }
        )
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Удалить задачу?") },
            text = {
                Column {
                    Text(task.title)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runRequest {
                        TaskApi.delete(task.id)
                        tasks = tasks.filterNot { it.id == task.id }
                        deleting = null
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}

private data class TaskEditor(val task: Task? = null, val title: String = "")
