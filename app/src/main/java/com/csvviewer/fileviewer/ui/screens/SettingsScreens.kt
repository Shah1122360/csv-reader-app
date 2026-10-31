@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.csvviewer.fileviewer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.FileUtil
import com.csvviewer.fileviewer.data.ThemeMode
import com.csvviewer.fileviewer.ui.*
import java.io.File

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavController) {
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<FileKind?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = vm.visibleFiles.filter { (q.isBlank() || it.name.contains(q, true)) && (kind == null || it.kind == kind) }
        .sortedByDescending { it.lastOpened }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, "Back") }
            OutlinedTextField(
                q, { q = it }, placeholder = { Text("Search files by name") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Clear, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
            listOf(FileKind.CSV, FileKind.EXCEL, FileKind.PDF, FileKind.JSON, FileKind.TEXT, FileKind.IMAGE).forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(k.label) })
            }
        }
        if (results.isEmpty()) EmptyState(Icons.Outlined.Search, "No results", if (q.isBlank()) "Files you've opened can be searched here." else "No file name contains \"$q\".")
        else LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(results, key = { it.uri }) { f -> FileRow(f, onClick = { nav.openFile(vm, f) }) }
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, nav: NavController) {
    val favs = vm.visibleFiles.filter { it.favorite }
    Scaffold(topBar = { AppTopBar("Favorites", onBack = { nav.popBackStack() }) }) { pad ->
        Box(Modifier.padding(pad)) {
            if (favs.isEmpty()) EmptyState(Icons.Outlined.StarBorder, "No favorites yet", "Tap ⋮ on any file and choose Add to favorites to keep it here.")
            else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(favs, key = { it.uri }) { f -> FileRow(f, onClick = { nav.openFile(vm, f) }, onFavorite = { vm.toggleFavorite(f) }) }
            }
        }
    }
}

@Composable
fun TrashScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        AppTopBar("Trash", onBack = { nav.popBackStack() }) {
            if (vm.trash.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Empty") }
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Text(
                "Removing a file here only takes it off the app's list. The original file on your phone is not deleted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (vm.trash.isEmpty()) EmptyState(Icons.Outlined.Delete, "Trash is empty", "Files you move to trash show up here so you can restore them.")
            else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(vm.trash, key = { it.uri }) { f ->
                    FileRow(f, onClick = {}, trailing = {
                        Row {
                            IconButton(onClick = { vm.restore(f); ctx.toast("Restored") }) { Icon(Icons.Outlined.Restore, "Restore") }
                            IconButton(onClick = { vm.removeForever(f) }) { Icon(Icons.Outlined.DeleteForever, "Remove") }
                        }
                    })
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Empty trash?") },
        text = { Text("${vm.trash.size} items will be removed from the app. Original files stay on your phone.") },
        confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirm = false }) { Text("Empty") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

@Composable
fun StorageScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var cacheSize by remember { mutableLongStateOf(FileUtil.dirSize(ctx.cacheDir)) }
    val stat = remember { StatFs(Environment.getDataDirectory().path) }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = total - free
    val byKind = vm.visibleFiles.groupBy { it.kind }.mapValues { (_, l) -> l.sumOf { it.size } }.entries.sortedByDescending { it.value }
    Scaffold(topBar = { AppTopBar("Storage analyzer", onBack = { nav.popBackStack() }) }, bottomBar = { AdSlot(vm.isPro, banner = true) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card16 {
                    Text("Phone storage", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { (used.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text("${FileUtil.size(used)} used of ${FileUtil.size(total)} · ${FileUtil.size(free)} free", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Card16 {
                    Text("Files in CSV Reader", style = MaterialTheme.typography.titleSmall)
                    if (byKind.isEmpty()) Text("No files yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    byKind.forEach { (k, s) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileIconBox(k, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(k.label, Modifier.weight(1f))
                            Text(FileUtil.size(s), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Card16 {
                    Text("App cache", style = MaterialTheme.typography.titleSmall)
                    Text("Converted files and previews: ${FileUtil.size(cacheSize)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        cacheSize = FileUtil.dirSize(ctx.cacheDir)
                        ctx.toast("Cache cleared")
                    }) { Icon(Icons.Outlined.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear cache") }
                }
            }
            item {
                SettingRow(Icons.Outlined.Delete, "Trash", "${vm.trash.size} items") { nav.navigate(Routes.TRASH) }
            }
        }
    }
}

@Composable
fun AppearanceScreen(vm: AppViewModel, nav: NavController) {
    Scaffold(topBar = { AppTopBar("Appearance", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple(ThemeMode.SYSTEM, "System default", "Follows your phone's setting"),
                Triple(ThemeMode.LIGHT, "Light", "Bright background"),
                Triple(ThemeMode.DARK, "Dark", "Easier on the eyes at night"),
            ).forEach { (m, title, sub) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.setTheme(m) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.themeMode == m, onClick = { vm.setTheme(m) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Preview", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Card16 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileIconBox(FileKind.CSV)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("sales_2026.csv", style = MaterialTheme.typography.titleSmall)
                        Text("CSV · 24 KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val languages = listOf("English", "اردو (Urdu)", "हिन्दी (Hindi)", "العربية (Arabic)", "Español", "Français", "Deutsch", "Português", "Bahasa Indonesia", "Türkçe")

@Composable
fun LanguageScreen(vm: AppViewModel, nav: NavController) {
    Scaffold(topBar = { AppTopBar("Language", onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text(
                    "This debug build is in English only. Your choice is saved and will apply once translations are added.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(languages) { l ->
                Row(Modifier.fillMaxWidth().clickable { vm.updateLanguage(l) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = vm.language == l, onClick = { vm.updateLanguage(l) })
                    Spacer(Modifier.width(8.dp))
                    Text(l, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    Scaffold(topBar = { AppTopBar("About", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(88)
            Spacer(Modifier.height(16.dp))
            Text("CSV Reader", style = MaterialTheme.typography.headlineSmall)
            Text("Version $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                "Open, edit, analyse and convert CSV, Excel, JSON, PDF and text files on your phone. Files stay on your device — nothing is uploaded.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { openPlayStore(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("View on Google Play") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { shareApp(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Share app") }
        }
    }
}

private val feedbackTopics = listOf("Bug", "Feature idea", "Conversion problem", "Design", "Other")

@Composable
fun FeedbackScreen(nav: NavController) {
    val ctx = LocalContext.current
    var topic by remember { mutableStateOf("Bug") }
    var message by remember { mutableStateOf("") }
    var device by remember { mutableStateOf(true) }
    Scaffold(
        topBar = { AppTopBar("Send feedback", onBack = { nav.popBackStack() }) },
        bottomBar = {
            Button(
                enabled = message.isNotBlank(),
                onClick = {
                    val body = buildString {
                        append(message)
                        if (device) append("\n\n—\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}")
                    }
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "CSV Reader feedback: $topic")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    try {
                        ctx.startActivity(Intent.createChooser(i, "Send with"))
                        nav.navigate(Routes.FEEDBACK_SENT) { popUpTo(Routes.FEEDBACK) { inclusive = true } }
                    } catch (_: Exception) {
                        ctx.toast("No email app found")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Send") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What is it about?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                feedbackTopics.forEach { t -> FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(t) }) }
            }
            OutlinedTextField(
                message, { message = it }, label = { Text("Your message") },
                placeholder = { Text("Tell us what happened or what you'd like to see") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { device = !device }) {
                Checkbox(device, { device = it })
                Text("Include phone model and Android version")
            }
            Text("Your email app opens with this message ready to send.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FeedbackSentScreen(nav: NavController) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BigCheck()
        Spacer(Modifier.height(20.dp))
        Text("Thanks for your feedback", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("We read every message and use it to improve CSV Reader.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
