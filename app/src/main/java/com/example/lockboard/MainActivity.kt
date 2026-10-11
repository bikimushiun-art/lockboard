package com.example.lockboard

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        Notifier.refresh(this)
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf(Storage.getNote(ctx)) }
    var tasks by remember { mutableStateOf(Storage.getTasks(ctx)) }
    var newTask by remember { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { Notifier.refresh(ctx) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun sync() {
        tasks = Storage.getTasks(ctx)
        Notifier.refresh(ctx)
    }

    Scaffold { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Заметка", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; Storage.setNote(ctx, it); Notifier.refresh(ctx) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Text("Задачи", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTask, onValueChange = { newTask = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Новая задача") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newTask.isNotBlank()) {
                        Storage.addTask(ctx, newTask.trim()); newTask = ""; sync()
                    }
                }) { Text("+") }
            }

            LazyColumn(Modifier.weight(1f)) {
                items(tasks, key = { it.id }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t.done, onCheckedChange = {
                            Storage.setDone(ctx, t.id, it); sync()
                        })
                        Text(
                            t.text,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null
                        )
                    }
                }
            }
            TextButton(onClick = { Storage.deleteDone(ctx); sync() }) {
                Text("Удалить выполненные")
            }
        }
    }
}
