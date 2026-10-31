@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.csvviewer.fileviewer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.FileItem
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.FileUtil
import com.csvviewer.fileviewer.ui.*

const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.csvviewer.fileviewer"

data class Tool(val id: String, val title: String, val subtitle: String, val icon: ImageVector, val pro: Boolean = false, val needsTable: Boolean = false)

val csvTools = listOf(
    Tool("viewer", "CSV viewer", "Open and read any CSV", Icons.Outlined.TableChart),
    Tool("editor", "CSV editor", "Edit cells, rows and columns", Icons.Outlined.Edit, pro = true, needsTable = true),
    Tool("create", "Create CSV", "Start a new table", Icons.Outlined.Add),
    Tool("analysis", "Data analysis", "Column statistics", Icons.Outlined.Assessment, needsTable = true),
    Tool("charts", "Charts", "Bar, line and pie charts", Icons.Outlined.BarChart, pro = true, needsTable = true),
    Tool("merge", "Merge CSV", "Join several CSV files", Icons.Outlined.Layers),
)

val convertTools = listOf(
    Tool("csv-pdf", "CSV to PDF", "Printable table", Icons.Outlined.PictureAsPdf),
    Tool("csv-xlsx", "CSV to Excel", ".xlsx workbook", Icons.Outlined.GridOn),
    Tool("csv-json", "CSV to JSON", "Array of objects", Icons.Outlined.Code),
    Tool("csv-html", "CSV to HTML", "Web table", Icons.Outlined.Language),
    Tool("csv-tsv", "CSV to TSV", "Tab-separated text", Icons.Outlined.Description),
    Tool("xlsx-csv", "Excel to CSV", "First sheet of .xlsx", Icons.Outlined.TableChart),
    Tool("json-csv", "JSON to CSV", "Array of objects or rows", Icons.Outlined.TableChart),
    Tool("tsv-csv", "Text/TSV to CSV", "Any delimited text", Icons.Outlined.TableChart),
    Tool("img-pdf", "Images to PDF", "One image per page", Icons.Outlined.PhotoLibrary),
)

val fileTools = listOf(
    Tool("batch", "Batch convert", "Convert many files at once", Icons.Outlined.FileCopy, pro = true),
    Tool("scanner", "Document scanner", "Scan paper to PDF", Icons.Outlined.CameraAlt),
    Tool("compress", "Compress to ZIP", "Pack files into one .zip", Icons.Outlined.Compress),
    Tool("zip", "ZIP viewer", "See what's inside a .zip", Icons.Outlined.Archive),
    Tool("storage", "Storage analyzer", "Space used by your files", Icons.Outlined.SdStorage),
)

fun shareApp(ctx: Context) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "CSV Reader")
        putExtra(Intent.EXTRA_TEXT, "I use CSV Reader to open, edit and convert CSV files: $PLAY_URL")
    }
    ctx.startActivity(Intent.createChooser(i, "Share CSV Reader"))
}

fun openPlayStore(ctx: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.csvviewer.fileviewer"))
    try { ctx.startActivity(market) } catch (_: Exception) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL)))
    }
}

@Composable
fun MainScreen(vm: AppViewModel, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExit by remember { mutableStateOf(false) }
    var showRate by rememberSaveable { mutableStateOf(false) }
    var askedRate by rememberSaveable { mutableStateOf(false) }
    var pendingRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        if (vm.shouldAskRating && !askedRate) { askedRate = true; showRate = true }
    }
    BackHandler { if (tab != 0) tab = 0 else showExit = true }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) nav.openFile(vm, vm.register(uri))
    }
    // Picks a table file, then opens the tool that needs it (editor, analysis, charts)
    val tablePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val route = pendingRoute
        if (uri != null && route != null) {
            vm.open(vm.register(uri))
            nav.navigate(route)
        }
    }
    val openPicker = { picker.launch(arrayOf("*/*")) }

    val runTool: (Tool) -> Unit = { t ->
        if (t.pro && !vm.isPro) nav.navigate(Routes.PREMIUM)
        else when (t.id) {
            "viewer" -> openPicker()
            "create" -> nav.navigate(Routes.CREATE)
            "editor", "analysis", "charts" -> {
                val route = mapOf("editor" to Routes.EDITOR, "analysis" to Routes.ANALYSIS, "charts" to Routes.CHARTS)[t.id]!!
                if (t.id == "editor") vm.startEditor(null, vm.current?.name ?: "file.csv")
                if (vm.table != null && vm.current != null) nav.navigate(route)
                else { pendingRoute = route; tablePicker.launch(arrayOf("*/*")) }
            }
            "merge" -> nav.navigate(Routes.MERGE)
            "batch" -> nav.navigate(Routes.BATCH)
            "scanner" -> nav.navigate(Routes.SCANNER)
            "compress" -> nav.navigate(Routes.COMPRESS)
            "zip" -> nav.navigate(Routes.ZIP)
            "storage" -> nav.navigate(Routes.STORAGE)
            else -> nav.navigate(Routes.convert(t.id))
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AdSlot(vm.isPro, banner = true)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val items = listOf(
                        Triple("Home", Icons.Outlined.Home, 0),
                        Triple("Files", Icons.Outlined.Folder, 1),
                        Triple("Tools", Icons.Outlined.Build, 2),
                        Triple("Settings", Icons.Outlined.Settings, 3),
                    )
                    items.forEach { (label, icon, i) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) ExtendedFloatingActionButton(
                onClick = openPicker,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Open file") },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeTab(vm, nav, openPicker, { tab = it }, runTool)
                1 -> FilesTab(vm, nav)
                2 -> ToolsTab(vm, runTool)
                else -> SettingsTab(vm, nav) { showRate = true }
            }
        }
    }

    if (showExit) ExitDialog(vm.isPro, onRate = { showExit = false; showRate = true }, onDismiss = { showExit = false }) {
        (ctx as? Activity)?.finish()
    }
    if (showRate) RateDialog(onDismiss = { showRate = false }) { stars ->
        showRate = false
        if (stars >= 4) openPlayStore(ctx) else nav.navigate(Routes.FEEDBACK)
    }
}

@Composable
private fun HomeTab(vm: AppViewModel, nav: NavController, openPicker: () -> Unit, setTab: (Int) -> Unit, runTool: (Tool) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val recent = vm.visibleFiles.sortedByDescending { it.lastOpened }
    val cont = recent.firstOrNull { it.kind.isTable && it.progress > 0f && it.progress < 0.99f }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("CSV Reader", style = MaterialTheme.typography.headlineSmall)
                    Text("Open, edit and convert your files", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (!vm.isPro) ProChip { nav.navigate(Routes.PREMIUM) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceVariant)
                    .clickable { nav.navigate(Routes.SEARCH) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Search files", color = cs.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Outlined.FolderOpen, "Open file", openPicker)
                QuickAction(Icons.Outlined.Add, "Create CSV") { nav.navigate(Routes.CREATE) }
                QuickAction(Icons.Outlined.CameraAlt, "Scan") { nav.navigate(Routes.SCANNER) }
                QuickAction(Icons.Outlined.SwapHoriz, "Convert") { setTab(2) }
            }
        }
        if (cont != null) item {
            SectionHeader("Continue reading")
            Card16(Modifier.clickable { nav.openFile(vm, cont) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileIconBox(cont.kind)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cont.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("Row ${cont.lastRow + 1} · ${(cont.progress * 100).toInt()}% read", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.KeyboardArrowRight, null)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { cont.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
            }
        }
        item { SectionHeader("Recent files", if (recent.isNotEmpty()) "See all" else null) { setTab(1) } }
        if (recent.isEmpty()) item {
            EmptyState(Icons.Outlined.Folder, "No files yet", "Open a CSV, Excel, JSON, PDF or text file and it will show up here.", "Open file", openPicker)
        }
        items(recent.take(5), key = { it.uri }) { f ->
            FileRow(
                f, onClick = { nav.openFile(vm, f) },
                onFavorite = { vm.toggleFavorite(f) },
                onShare = { FileUtil.shareUri(ctx, Uri.parse(f.uri), f.mime) },
                onTrash = { vm.moveToTrash(f) },
            )
        }
        item { Spacer(Modifier.height(12.dp)); AdSlot(vm.isPro) }
        item { SectionHeader("Popular tools", "All tools") { setTab(2) } }
        item {
            val popular = listOf(convertTools[0], convertTools[1], convertTools[5], convertTools[6], csvTools[4], fileTools[0])
            ToolGrid(popular, runTool)
        }
    }
}

@Composable
fun ToolGrid(tools: List<Tool>, runTool: (Tool) -> Unit) {
    val tints = LocalTints.current
    val colors = listOf(tints.csv, tints.pdf, tints.excel, tints.json, tints.text, tints.zip, tints.image)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(3).forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { ci, t ->
                    ToolTile(t.icon, t.title, t.pro, colors[(ri * 3 + ci) % colors.size], { runTool(t) }, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

private val fileFilters = listOf("All", "CSV", "Excel", "PDF", "Text", "Images", "Other")

private fun matches(filter: String, k: FileKind) = when (filter) {
    "CSV" -> k == FileKind.CSV || k == FileKind.TSV
    "Excel" -> k == FileKind.EXCEL
    "PDF" -> k == FileKind.PDF
    "Text" -> k == FileKind.TEXT || k == FileKind.JSON
    "Images" -> k == FileKind.IMAGE
    "Other" -> k == FileKind.ZIP || k == FileKind.OTHER
    else -> true
}

@Composable
private fun FilesTab(vm: AppViewModel, nav: NavController) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    var sortMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<FileItem?>(null) }
    val ctx = LocalContext.current
    val list = vm.visibleFiles.filter { matches(filter, it.kind) }.let { l ->
        when (sort) {
            "Name" -> l.sortedBy { it.name.lowercase() }
            "Size" -> l.sortedByDescending { it.size }
            else -> l.sortedByDescending { it.lastOpened }
        }
    }
    Column {
        AppTopBar("Files", actions = {
            IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, "Search") }
            IconButton(onClick = { nav.navigate(Routes.FAVORITES) }) { Icon(Icons.Outlined.StarBorder, "Favorites") }
            IconButton(onClick = { nav.navigate(Routes.TRASH) }) { Icon(Icons.Outlined.Delete, "Trash") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "Sort") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Recent", "Name", "Size").forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, trailingIcon = { if (s == sort) Icon(Icons.Outlined.Check, null) }, onClick = { sort = s; sortMenu = false })
                    }
                }
            }
        })
        Row(Modifier.fillMaxWidth().horizontalScrollCompat().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fileFilters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.FolderOpen, if (filter == "All") "No files yet" else "No $filter files", "Files you open are listed here. Tap Open file to pick one from your phone.")
        } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            items(list, key = { it.uri }) { f ->
                FileRow(
                    f, onClick = { nav.openFile(vm, f) },
                    onFavorite = { vm.toggleFavorite(f) },
                    onShare = { FileUtil.shareUri(ctx, Uri.parse(f.uri), f.mime) },
                    onTrash = { vm.moveToTrash(f); ctx.toast("Moved to trash") },
                    onInfo = { info = f },
                )
            }
            item { Spacer(Modifier.height(8.dp)); AdSlot(vm.isPro) }
        }
    }
    info?.let { f ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Close") } },
            title = { Text("File info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${f.name}")
                    Text("Type: ${f.kind.label}")
                    Text("Size: ${FileUtil.size(f.size)}")
                    Text("Last opened: ${FileUtil.time(f.lastOpened)}")
                }
            },
        )
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun ToolsTab(vm: AppViewModel, runTool: (Tool) -> Unit) {
    val tints = LocalTints.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item { AppTopBar("Tools") }
        item { SectionHeader("CSV tools"); ToolGrid(csvTools, runTool) }
        item { SectionHeader("Convert") }
        items(convertTools) { t -> ToolRow(t.icon, t.title, t.subtitle, tints.pdf.takeIf { t.id.endsWith("pdf") } ?: tints.csv, t.pro) { runTool(t) } }
        item { Spacer(Modifier.height(8.dp)); AdSlot(vm.isPro) }
        item { SectionHeader("Batch & files") }
        items(fileTools) { t -> ToolRow(t.icon, t.title, t.subtitle, tints.zip, t.pro) { runTool(t) } }
    }
}

@Composable
private fun SettingsTab(vm: AppViewModel, nav: NavController, onRate: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppTopBar("Settings") }
        if (!vm.isPro) item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Gold1, Gold2)))
                    .clickable { nav.navigate(Routes.PREMIUM) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Upgrade to Premium", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("No ads, editor, charts and batch convert", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = Color.White)
            }
        }
        item { SettingsGroup("General") }
        item {
            SettingRow(Icons.Outlined.Brightness6, "Appearance", vm.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }) { nav.navigate(Routes.APPEARANCE) }
            SettingRow(Icons.Outlined.Translate, "Language", vm.language) { nav.navigate(Routes.LANGUAGE) }
            SettingRow(Icons.Outlined.SdStorage, "Storage analyzer") { nav.navigate(Routes.STORAGE) }
            SettingRow(Icons.Outlined.StarBorder, "Favorites") { nav.navigate(Routes.FAVORITES) }
            SettingRow(Icons.Outlined.Delete, "Trash", "${vm.trash.size} items") { nav.navigate(Routes.TRASH) }
            SettingRow(Icons.Outlined.History, "Clear recent files") { vm.clearRecents(); ctx.toast("Recent files cleared") }
        }
        item { Box(Modifier.padding(16.dp)) { AdSlot(vm.isPro) } }
        item { SettingsGroup("Support") }
        item {
            SettingRow(Icons.Outlined.ThumbUp, "Rate us", onClick = onRate)
            SettingRow(Icons.Outlined.Share, "Share app") { shareApp(ctx) }
            SettingRow(Icons.Outlined.Feedback, "Send feedback") { nav.navigate(Routes.FEEDBACK) }
            SettingRow(Icons.Outlined.Info, "About") { nav.navigate(Routes.ABOUT) }
        }
        item { SettingsGroup("Debug build") }
        item {
            SettingRow(Icons.Outlined.EmojiEvents, "Premium (testing)", if (vm.isPro) "Unlocked locally" else "Free",
                trailing = { Switch(checked = vm.isPro, onCheckedChange = { vm.updatePro(it) }) }) { vm.updatePro(!vm.isPro) }
            SettingRow(Icons.Outlined.Refresh, "Show onboarding again") {
                vm.resetOnboarding(); ctx.toast("Onboarding will show on next launch")
            }
        }
        item {
            Text(
                "CSV Reader 2.0 debug", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SettingsGroup(title: String) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun ExitDialog(isPro: Boolean, onRate: () -> Unit, onDismiss: () -> Unit, onExit: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Exit CSV Reader?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Your recent files will be here next time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            AdSlot(isPro)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRate, modifier = Modifier.weight(1f)) { Text("Rate us") }
                Button(onClick = onExit, modifier = Modifier.weight(1f)) { Text("Exit") }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
fun RateDialog(onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    var stars by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enjoying CSV Reader?", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Tap a star to rate the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row {
                    for (i in 1..5) {
                        IconButton(onClick = { stars = i }) {
                            Icon(
                                if (i <= stars) Icons.Filled.Star else Icons.Outlined.StarBorder, "$i stars",
                                tint = if (i <= stars) Gold1 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                if (stars in 1..3) Text("Tell us what to improve on the next screen.", style = MaterialTheme.typography.bodySmall)
                if (stars >= 4) Text("Thanks! You'll go to Google Play to post it.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = stars > 0, onClick = { onSubmit(stars) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
