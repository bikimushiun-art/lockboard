package com.example.lockboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Bg = Color(0xFFE3DFD8)
val Ink = Color(0xFF161614)
val Orange = Color(0xFFFF6A13)
val CardBg = Color(0xFFF2EFEA)
val Muted = Color(0xFF6B665E)

class MainActivity : ComponentActivity() {
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Ink, onPrimary = Color.White,
                    secondary = Orange, onSecondary = Color.White,
                    background = Bg, onBackground = Ink,
                    surface = CardBg, onSurface = Ink,
                    surfaceVariant = Color(0xFFD9D4CC), onSurfaceVariant = Muted,
                    outline = Muted
                )
            ) { Root(resumes.intValue) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrEmpty()) {
            Storage.appendNote(this, text)
            Toast.makeText(this, "Добавлено в заметку", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++      // перечитать данные: отметки могли быть сделаны из уведомлений
        Notifier.refresh(this)
    }
}

@Composable
fun Root(resume: Int) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val key = resume * 100000 + tick
    val onChange = { tick++; Notifier.refresh(ctx) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { Notifier.refresh(ctx) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                val items = listOf(
                    Triple("Трекеры", Icons.Default.DateRange, 0),
                    Triple("Задачи", Icons.Default.Check, 1),
                    Triple("Заметка", Icons.Default.Edit, 2)
                )
                items.forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Ink,
                            indicatorColor = Ink
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> TrackersScreen(key, onChange)
                1 -> TasksScreen(key, onChange)
                else -> NoteScreen(key)
            }
        }
    }
}
