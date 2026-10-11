package com.example.lockboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val PALETTE = listOf(
    0xFFFF6A13, 0xFFE5484D, 0xFF2F9E6B, 0xFF3E7BD8,
    0xFF8E5BD0, 0xFFD6A100, 0xFF1F8FA3, 0xFF7A8699
).map { it.toInt() }

// ======================= общие диалоги =======================

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить", color = Color(0xFFC62828)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun TrackerDialog(initial: Tracker?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: PALETTE[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(if (initial == null) "Новый трекер" else "Трекер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                PALETTE.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { col ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(col))
                                    .border(if (col == color) 3.dp else 0.dp, Ink, CircleShape)
                                    .clickable { color = col }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ======================= трекеры =======================

@Composable
private fun WeekRow(color: Color, monday: LocalDate, checks: Set<String>, onToggle: (String) -> Unit) {
    val letters = "ПВСЧПСВ"
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val on = d.toString() in checks
            val isToday = d == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(letters[i].toString(), fontSize = 11.sp, color = if (isToday) Orange else Muted)
                Spacer(Modifier.size(4.dp))
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .border(if (isToday) 2.dp else 0.dp, Orange, CircleShape)
                        .clickable { onToggle(d.toString()) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .then(if (on) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    d.dayOfMonth.toString(), fontSize = 11.sp,
                    color = if (isToday) Orange else Muted,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TrackersScreen(key: Any, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val trackers = remember(key) { Storage.getTrackers(ctx) }
    var offset by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tracker?>(null) }
    var deleting by remember { mutableStateOf<Tracker?>(null) }
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Трекеры", fontSize = 32.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset-- }) { Text("‹", fontSize = 24.sp, color = Ink) }
                    Text(
                        "${monday.format(fmt)} – ${monday.plusDays(6).format(fmt)}",
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted
                    )
                    TextButton(onClick = { offset++ }) { Text("›", fontSize = 24.sp, color = Ink) }
                }
            }
            items(trackers, key = { it.id }) { t ->
                val checks = remember(key) { Storage.getChecks(ctx, t.id) }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.color)))
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { editing = t }) { Icon(Icons.Default.Edit, "Изменить", tint = Muted) }
                            IconButton(onClick = { deleting = t }) { Icon(Icons.Default.Delete, "Удалить", tint = Muted) }
                        }
                        WeekRow(Color(t.color), monday, checks) { date ->
                            Storage.toggleDay(ctx, t.id, date); onChange()
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Ink, contentColor = Color.White,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Трекер") }
        )
    }

    if (adding) TrackerDialog(null, { adding = false }) { n, c ->
        Storage.addTracker(ctx, n, c); adding = false; onChange()
    }
    editing?.let { t ->
        TrackerDialog(t, { editing = null }) { n, c ->
            Storage.updateTracker(ctx, t.id, n, c); editing = null; onChange()
        }
    }
    deleting?.let { t ->
        ConfirmDialog("Удалить трекер «${t.name}» вместе с отметками?", { deleting = null }) {
            Storage.deleteTracker(ctx, t.id); deleting = null; onChange()
        }
    }
}

// ======================= задачи =======================

private sealed class Dlg {
    object NewGroup : Dlg()
    class RenameGroup(val g: Group) : Dlg()
    class DeleteGroup(val g: Group) : Dlg()
    class NewTask(val g: Group) : Dlg()
    class RenameTask(val t: Task) : Dlg()
}

@Composable
fun TasksScreen(key: Any, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(key) { Storage.getGroups(ctx) }
    val tasks = remember(key) { Storage.getTasks(ctx) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Задачи", Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { Storage.deleteDone(ctx); onChange() }) {
                        Text("Убрать выполненные", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            items(groups, key = { it.id }) { g ->
                val list = tasks.filter { it.groupId == g.id }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { dlg = Dlg.NewTask(g) }) { Icon(Icons.Default.Add, "Добавить задачу") }
                            IconButton(onClick = { dlg = Dlg.RenameGroup(g) }) { Icon(Icons.Default.Edit, "Переименовать", tint = Muted) }
                            IconButton(onClick = { dlg = Dlg.DeleteGroup(g) }) { Icon(Icons.Default.Delete, "Удалить группу", tint = Muted) }
                        }
                        if (list.isEmpty()) {
                            Text("Пока пусто", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        list.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = t.done,
                                    onCheckedChange = { Storage.setDone(ctx, t.id, it); onChange() },
                                    colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = Muted)
                                )
                                Text(
                                    t.text,
                                    modifier = Modifier.weight(1f).clickable { dlg = Dlg.RenameTask(t) },
                                    color = if (t.done) Muted else Ink,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                                )
                                IconButton(onClick = { Storage.deleteTask(ctx, t.id); onChange() }) {
                                    Icon(Icons.Default.Delete, "Удалить задачу", tint = Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { dlg = Dlg.NewGroup },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Ink, contentColor = Color.White,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Группа") }
        )
    }

    when (val d = dlg) {
        is Dlg.NewGroup -> TextDialog("Новая группа", "", { dlg = null }) {
            Storage.addGroup(ctx, it); dlg = null; onChange()
        }
        is Dlg.RenameGroup -> TextDialog("Название группы", d.g.name, { dlg = null }) {
            Storage.renameGroup(ctx, d.g.id, it); dlg = null; onChange()
        }
        is Dlg.DeleteGroup -> ConfirmDialog("Удалить группу «${d.g.name}» со всеми задачами?", { dlg = null }) {
            Storage.deleteGroup(ctx, d.g.id); dlg = null; onChange()
        }
        is Dlg.NewTask -> TextDialog("Новая задача · ${d.g.name}", "", { dlg = null }) {
            Storage.addTask(ctx, d.g.id, it); dlg = null; onChange()
        }
        is Dlg.RenameTask -> TextDialog("Задача", d.t.text, { dlg = null }) {
            Storage.renameTask(ctx, d.t.id, it); dlg = null; onChange()
        }
        null -> {}
    }
}

// ======================= заметка =======================

@Composable
fun NoteScreen(key: Any) {
    val ctx = LocalContext.current
    var text by remember(key) { mutableStateOf(Storage.getNote(ctx)) }

    // сохраняем с небольшой задержкой, чтобы не перерисовывать уведомление на каждую букву
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text != Storage.getNote(ctx)) {
            Storage.setNote(ctx, text)
            Notifier.refresh(ctx)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Заметка", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Что запомнить?") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                focusedIndicatorColor = Orange, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Orange
            )
        )
    }
}
