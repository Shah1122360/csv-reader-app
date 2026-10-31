@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.csvviewer.fileviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.CsvTable
import com.csvviewer.fileviewer.ui.*
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(vm: AppViewModel, nav: NavController) {
    // Editing an existing file is Premium; a table made with "Create CSV" is free
    if (!vm.isPro && !vm.editorIsNew) {
        LaunchedEffect(Unit) { nav.navigate(Routes.PREMIUM) { popUpTo(Routes.EDITOR) { inclusive = true } } }
        return
    }
    val src = vm.table
    if (src == null) {
        Column { AppTopBar("Editor", onBack = { nav.popBackStack() }); TableStateBox(vm) {} }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var header by remember(src) { mutableStateOf(src.header) }
    var rows by remember(src) { mutableStateOf(src.rows) }
    var dirty by remember(src) { mutableStateOf(vm.editorIsNew) }
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var colDialog by remember { mutableStateOf<Int?>(null) }
    var rowMenu by remember { mutableStateOf<Int?>(null) }
    val undo = remember { mutableStateListOf<Pair<List<String>, List<List<String>>>>() }

    fun push() {
        undo.add(header to rows)
        if (undo.size > 30) undo.removeAt(0)
        dirty = true
    }
    fun table() = CsvTable(header, rows)
    fun setCell(r: Int, c: Int, v: String) {
        rows = rows.toMutableList().also { list -> list[r] = list[r].toMutableList().also { it[c] = v } }
        dirty = true
    }
    fun insertRow(at: Int) { push(); rows = rows.toMutableList().also { it.add(at.coerceIn(0, rows.size), List(header.size) { "" }) } }
    fun insertCol(at: Int, name: String) {
        push()
        val i = at.coerceIn(0, header.size)
        header = header.toMutableList().also { it.add(i, name) }
        rows = rows.map { r -> r.toMutableList().also { it.add(i, "") } }
    }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            saving = true
            val err = vm.saveTable(table(), uri)
            saving = false
            if (err == null) {
                dirty = false
                vm.register(uri)
                ctx.toast("Saved")
            } else ctx.toast(err)
        }
    }
    fun save() {
        val cur = vm.current
        if (cur == null || vm.editorIsNew) saveAs.launch(vm.editorName)
        else scope.launch {
            saving = true
            val err = vm.saveTable(table(), Uri.parse(cur.uri))
            saving = false
            if (err == null) { dirty = false; ctx.toast("Saved") }
            else { ctx.toast("Can't overwrite: $err. Choose where to save a copy."); saveAs.launch(cur.name) }
        }
    }

    BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(
        topBar = {
            AppTopBar(
                vm.current?.name ?: vm.editorName,
                onBack = { if (dirty) confirmLeave = true else nav.popBackStack() },
                subtitle = "${rows.size} rows · ${header.size} columns" + if (dirty) " · unsaved" else "",
            ) {
                IconButton(enabled = undo.isNotEmpty(), onClick = {
                    val (h, r) = undo.removeAt(undo.lastIndex); header = h; rows = r
                }) { Icon(Icons.Outlined.Undo, "Undo") }
                IconButton(onClick = { insertCol(header.size, "Column ${header.size + 1}") }) { Icon(Icons.Outlined.ViewColumn, "Add column") }
                IconButton(enabled = !saving, onClick = { save() }) { Icon(Icons.Outlined.Save, "Save") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { insertRow(rows.size) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add row") })
        },
    ) { pad ->
        val hs = rememberScrollState()
        val cellW = 150.dp
        val numW = 52.dp
        Box(Modifier.padding(pad).fillMaxSize().horizontalScroll(hs)) {
            LazyColumn(Modifier.width(numW + cellW * header.size).fillMaxHeight(), contentPadding = PaddingValues(bottom = 96.dp)) {
                stickyHeader {
                    Row(Modifier.background(cs.primaryContainer)) {
                        Text("#", Modifier.width(numW).padding(10.dp), color = cs.onPrimaryContainer)
                        header.forEachIndexed { i, h ->
                            Row(Modifier.width(cellW).clickable { colDialog = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(h, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ArrowDropDown, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                itemsIndexed(rows) { r, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Text("${r + 1}", Modifier.width(numW).clickable { rowMenu = r }.padding(10.dp), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            DropdownMenu(rowMenu == r, { rowMenu = null }) {
                                DropdownMenuItem(text = { Text("Insert row above") }, onClick = { rowMenu = null; insertRow(r) })
                                DropdownMenuItem(text = { Text("Insert row below") }, onClick = { rowMenu = null; insertRow(r + 1) })
                                DropdownMenuItem(text = { Text("Duplicate row") }, onClick = { rowMenu = null; push(); rows = rows.toMutableList().also { it.add(r + 1, row) } })
                                DropdownMenuItem(text = { Text("Delete row") }, onClick = { rowMenu = null; push(); rows = rows.toMutableList().also { it.removeAt(r) } })
                            }
                        }
                        row.forEachIndexed { c, v ->
                            BasicTextField(
                                value = v, onValueChange = { setCell(r, c, it) }, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                                cursorBrush = SolidColor(cs.primary),
                                modifier = Modifier.width(cellW).border(0.5.dp, cs.outlineVariant).padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    colDialog?.let { c ->
        var name by remember(c) { mutableStateOf(header.getOrElse(c) { "" }) }
        AlertDialog(
            onDismissRequest = { colDialog = null },
            title = { Text("Column") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { colDialog = null; insertCol(c, "New column") }, label = { Text("Insert left") })
                        AssistChip(onClick = { colDialog = null; insertCol(c + 1, "New column") }, label = { Text("Insert right") })
                        if (header.size > 1) AssistChip(onClick = {
                            colDialog = null; push()
                            header = header.toMutableList().also { it.removeAt(c) }
                            rows = rows.map { r -> r.toMutableList().also { it.removeAt(c) } }
                        }, label = { Text("Delete column") }, leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name != header[c]) { push(); header = header.toMutableList().also { it[c] = name } }
                    colDialog = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { colDialog = null }) { Text("Cancel") } },
        )
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved edits in this table.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; save() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false; dirty = false; nav.popBackStack() }) { Text("Discard") } },
    )
}

private val templates = linkedMapOf(
    "Blank" to listOf("Column 1", "Column 2", "Column 3"),
    "Contacts" to listOf("Name", "Phone", "Email", "City"),
    "Expenses" to listOf("Date", "Item", "Category", "Amount"),
    "Inventory" to listOf("SKU", "Product", "Quantity", "Price"),
    "Attendance" to listOf("Date", "Name", "Status"),
)

@Composable
fun CreateCsvScreen(vm: AppViewModel, nav: NavController) {
    var name by rememberSaveable { mutableStateOf("new_file") }
    var template by rememberSaveable { mutableStateOf("Blank") }
    val cols = remember { mutableStateListOf(*templates["Blank"]!!.toTypedArray()) }
    var rowCount by rememberSaveable { mutableIntStateOf(10) }
    Scaffold(
        topBar = { AppTopBar("Create CSV", onBack = { nav.popBackStack() }) },
        bottomBar = {
            Button(
                onClick = {
                    val header = cols.map { it.trim() }.mapIndexed { i, s -> s.ifBlank { "Column ${i + 1}" } }
                    vm.startEditor(CsvTable(header, List(rowCount) { List(header.size) { "" } }), name.trim().ifBlank { "new_file" }.removeSuffix(".csv") + ".csv")
                    nav.navigate(Routes.EDITOR) { popUpTo(Routes.CREATE) { inclusive = true } }
                },
                enabled = cols.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Create and edit") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("File name") }, suffix = { Text(".csv") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("Template", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.keys.forEach { k ->
                        FilterChip(selected = template == k, onClick = { template = k; cols.clear(); cols.addAll(templates[k]!!) }, label = { Text(k) })
                    }
                }
            }
            item { Text("Columns", style = MaterialTheme.typography.titleSmall) }
            items(cols.size) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(cols[i], { cols[i] = it }, label = { Text("Column ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { cols.removeAt(i) }, enabled = cols.size > 1) { Icon(Icons.Outlined.Close, "Remove column") }
                }
            }
            item { TextButton(onClick = { cols.add("Column ${cols.size + 1}") }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add column") } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Empty rows to start with", Modifier.weight(1f))
                    IconButton(onClick = { if (rowCount > 1) rowCount-- }) { Icon(Icons.Outlined.Remove, "Fewer") }
                    Text("$rowCount", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (rowCount < 500) rowCount++ }) { Icon(Icons.Outlined.Add, "More") }
                }
            }
        }
    }
}
