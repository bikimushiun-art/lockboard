package com.example.lockboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay

private val PALETTE = listOf(
    0xFFFF6A13, 0xFFE5484D, 0xFF2F9E6B, 0xFF3E7BD8,
    0xFF8E5BD0, 0xFFD6A100, 0xFF1F8FA3, 0xFF7A8699
).map { it.toInt() }

private fun streak(checks: Set<String>): Int {
    var d = LocalDate.now()
    if (d.toString() !in checks) d = d.minusDays(1)
    var n = 0
    while (d.toString() in checks) { n++; d = d.minusDays(1) }
    return n
}

// ======================= мелкие элементы =======================

@Composable
private fun Tap(icon: ImageVector, desc: String, tint: Color = Muted, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }
}

/**
 * Кружок дня. Пусто — пунктир; отмечено — заливка без обводки;
 * сегодня — сплошная тёмная обводка, а если отмечено — тёмная заливка.
 */
@Composable
private fun DayDot(
    label: String, on: Boolean, today: Boolean, color: Color,
    dia: Dp, showCheck: Boolean, onClick: () -> Unit
) {
    val deco = when {
        today && on -> Modifier.background(Ink)
        on -> Modifier.background(color)
        today -> Modifier.border(2.dp, Ink, CircleShape)
        else -> Modifier.drawBehind {
            drawCircle(
                color,
                radius = this.size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                )
            )
        }
    }
    val fg = when { today && on -> color; on -> Color.White; today -> Ink; else -> Muted }
    Box(
        Modifier.size(dia).clip(CircleShape).then(deco).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (on && showCheck) Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(dia * 0.55f))
        else Text(
            label, fontSize = (dia.value * 0.36f).sp, color = fg,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ======================= диалоги =======================

@Composable
fun TextDialog(
    title: String, initial: String, onDismiss: () -> Unit,
    onAddMore: ((String) -> Unit)? = null,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {            // клавиатура сразу при открытии
        delay(150)
        focus.requestFocus()
        keyboard?.show()
    }
    fun addMore() {
        if (text.isNotBlank() && onAddMore != null) { onAddMore(text.trim()); text = "" }
    }
    fun save() {
        when {
            text.isNotBlank() -> onConfirm(text.trim())
            onAddMore != null -> onDismiss()   // после «+ Новая» пустое поле = просто закрыть
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title, fontSize = 20.sp) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (onAddMore != null) addMore() else save() })
            )
        },
        confirmButton = {
            Row {
                if (onAddMore != null) TextButton(onClick = { addMore() }) { Text("+ Новая") }
                TextButton(onClick = { save() }) { Text("Сохранить") }
            }
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) { delay(150); focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(if (initial == null) "Новый трекер" else "Трекер", fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
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
            DayDot(letters[i].toString(), d.toString() in checks, d == today, color, 38.dp, true) {
                onToggle(d.toString())
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
    var detailId by remember { mutableStateOf<Int?>(null) }
    val detail = trackers.firstOrNull { it.id == detailId }
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))

    if (detail != null) {
        TrackerDetail(
            detail, key, onChange,
            onBack = { detailId = null },
            onEdit = { editing = detail },
            onDelete = { deleting = detail }
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("Трекеры", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { offset-- }) { Text("‹", fontSize = 22.sp, color = Ink) }
                        Text(
                            "${monday.format(fmt)} – ${monday.plusDays(6).format(fmt)}",
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            color = Muted, fontSize = 13.sp
                        )
                        TextButton(onClick = { offset++ }) { Text("›", fontSize = 22.sp, color = Ink) }
                    }
                }
                items(trackers, key = { it.id }) { t ->
                    val checks = remember(key) { Storage.getChecks(ctx, t.id) }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { detailId = t.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.color)))
                                Spacer(Modifier.width(8.dp))
                                Text(t.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("серия ${streak(checks)}", color = Muted, fontSize = 12.sp)
                                Text("  ›", color = Muted, fontSize = 18.sp)
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
            Storage.deleteTracker(ctx, t.id); deleting = null; detailId = null; onChange()
        }
    }
}

@Composable
private fun TrackerDetail(
    t: Tracker, key: Any, onChange: () -> Unit,
    onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val checks = remember(key) { Storage.getChecks(ctx, t.id) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()
    val color = Color(t.color)
    val inMonth = checks.count { runCatching { YearMonth.from(LocalDate.parse(it)) == month }.getOrDefault(false) }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
        .replaceFirstChar { it.uppercase() }
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Назад", color = Ink) }
                Spacer(Modifier.weight(1f))
                Tap(Icons.Default.Edit, "Изменить", onClick = onEdit)
                Tap(Icons.Default.Delete, "Удалить", onClick = onDelete)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(t.name, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Серия: ${streak(checks)}", "За месяц: $inMonth", "Всего: ${checks.size}").forEach {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(it, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 22.sp, color = Ink) }
                        Text(monthLabel, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { month = month.plusMonths(1) }) { Text("›", fontSize = 22.sp, color = Ink) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
                        }
                    }
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val n = r * 7 + col - lead + 1
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (n in 1..days) {
                                        val d = month.atDay(n)
                                        DayDot(n.toString(), d.toString() in checks, d == today, color, 40.dp, false) {
                                            Storage.toggleDay(ctx, t.id, d.toString()); onChange()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
private fun TaskCheck(done: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(22.dp).clip(shape)
            .then(if (done) Modifier.background(Muted) else Modifier.border(2.dp, Ink, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
fun TasksScreen(key: Any, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(key) { Storage.getGroups(ctx) }
    val tasks = remember(key) { Storage.getTasks(ctx) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }
    var menuFor by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Задачи", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { Storage.deleteDone(ctx); onChange() }) {
                        Text("Убрать выполненные", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            items(groups, key = { it.id }) { g ->
                val list = tasks.filter { it.groupId == g.id }.sortedBy { it.done }
                val isCollapsed = g.id in collapsed
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                collapsed = if (isCollapsed) collapsed - g.id else collapsed + g.id
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.name, Modifier.weight(1f).padding(vertical = 6.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${list.count { it.done }} / ${list.size}", color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Tap(Icons.Default.Add, "Добавить задачу", Ink) { dlg = Dlg.NewTask(g) }
                            Box {
                                Tap(Icons.Default.MoreVert, "Меню") { menuFor = g.id }
                                DropdownMenu(expanded = menuFor == g.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menuFor = null; dlg = Dlg.RenameGroup(g) })
                                    DropdownMenuItem(text = { Text("Удалить группу") }, onClick = { menuFor = null; dlg = Dlg.DeleteGroup(g) })
                                }
                            }
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                null, tint = Muted
                            )
                        }
                        if (!isCollapsed) {
                            if (list.isEmpty()) Text("Пока пусто", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                            list.forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TaskCheck(t.done) { Storage.setDone(ctx, t.id, !t.done); onChange() }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        t.text,
                                        modifier = Modifier.weight(1f).clickable { dlg = Dlg.RenameTask(t) },
                                        fontSize = 15.sp,
                                        color = if (t.done) Muted else Ink,
                                        textDecoration = if (t.done) TextDecoration.LineThrough else null
                                    )
                                    Tap(Icons.Default.Delete, "Удалить задачу") { Storage.deleteTask(ctx, t.id); onChange() }
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
        is Dlg.NewTask -> TextDialog(
            "Новая задача · ${d.g.name}", "", { dlg = null },
            onAddMore = { Storage.addTask(ctx, d.g.id, it); onChange() }
        ) { Storage.addTask(ctx, d.g.id, it); dlg = null; onChange() }
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

    LaunchedEffect(text) {
        delay(600)
        if (text != Storage.getNote(ctx)) {
            Storage.setNote(ctx, text)
            Notifier.refresh(ctx, setOf(Notifier.Part.NOTE))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Заметка", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("Что запомнить?") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                focusedIndicatorColor = Orange, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Orange
            )
        )
    }
}
