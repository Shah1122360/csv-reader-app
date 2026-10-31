@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.csvviewer.fileviewer.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.Analysis
import com.csvviewer.fileviewer.data.ColumnStats
import com.csvviewer.fileviewer.data.CsvTable
import com.csvviewer.fileviewer.data.FileUtil
import com.csvviewer.fileviewer.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

fun fmt(d: Double?): String {
    if (d == null) return "—"
    return if (d == Math.floor(d) && Math.abs(d) < 1e15) String.format(Locale.US, "%,.0f", d)
    else String.format(Locale.US, "%,.2f", d)
}

private fun compareCells(a: String, b: String): Int {
    val x = Analysis.toNumber(a)
    val y = Analysis.toNumber(b)
    return if (x != null && y != null) x.compareTo(y) else a.compareTo(b, ignoreCase = true)
}

@Composable
fun TableStateBox(vm: AppViewModel, content: @Composable (CsvTable) -> Unit) {
    val t = vm.table
    when {
        vm.loading -> LoadingBox("Opening file…")
        vm.error != null -> EmptyState(Icons.Outlined.Info, "Couldn't open this file", vm.error ?: "")
        t == null -> EmptyState(Icons.Outlined.TableChart, "No file open", "Open a CSV, Excel or JSON file first.")
        else -> content(t)
    }
}

@Composable
fun ViewerScreen(vm: AppViewModel, nav: NavController) {
    val f = vm.current
    val t = vm.table
    val ctx = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var raw by rememberSaveable { mutableStateOf(false) }
    var sortCol by rememberSaveable { mutableIntStateOf(-1) }
    var asc by rememberSaveable { mutableStateOf(true) }
    var colSheet by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lastIdx = remember { mutableIntStateOf(0) }

    val shown = remember(t, query, sortCol, asc) {
        if (t == null) emptyList() else {
            var l = t.rows.mapIndexed { i, r -> i to r }
            if (query.isNotBlank()) l = l.filter { (_, r) -> r.any { it.contains(query, ignoreCase = true) } }
            if (sortCol >= 0) {
                l = l.sortedWith { a, b -> compareCells(a.second.getOrElse(sortCol) { "" }, b.second.getOrElse(sortCol) { "" }) }
                if (!asc) l = l.reversed()
            }
            l
        }
    }

    // Continue reading: restore the last row, remember where the user stopped
    LaunchedEffect(t) {
        val row = f?.lastRow ?: 0
        if (t != null && row > 0 && row < t.rows.size) listState.scrollToItem(row + 1)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { lastIdx.intValue = (it - 1).coerceAtLeast(0) }
    }
    DisposableEffect(Unit) { onDispose { if (vm.table != null) vm.saveProgress(lastIdx.intValue) } }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Outlined.ArrowBack, "Close search") } },
                    title = {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search in table") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, "Clear") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            } else AppTopBar(
                f?.name ?: "Viewer", onBack = { nav.popBackStack() },
                subtitle = t?.let { "${it.rows.size} rows · ${it.colCount} columns" },
            ) {
                IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Search") }
                IconButton(onClick = { raw = !raw }) { Icon(if (raw) Icons.Outlined.TableChart else Icons.Outlined.Code, if (raw) "Table view" else "Raw view") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        if (f != null) DropdownMenuItem(
                            text = { Text(if (f.favorite) "Remove favorite" else "Add to favorites") },
                            leadingIcon = { Icon(if (f.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                            onClick = { menu = false; vm.toggleFavorite(f) },
                        )
                        DropdownMenuItem(text = { Text("Data analysis") }, leadingIcon = { Icon(Icons.Outlined.Assessment, null) }, onClick = { menu = false; nav.navigate(Routes.ANALYSIS) })
                        DropdownMenuItem(text = { Text("Charts") }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, trailingIcon = { if (!vm.isPro) ProBadge() }, onClick = { menu = false; nav.pro(vm, Routes.CHARTS) })
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, trailingIcon = { if (!vm.isPro) ProBadge() }, onClick = {
                            menu = false; vm.startEditor(null, f?.name ?: "file.csv"); nav.pro(vm, Routes.EDITOR)
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Convert to PDF") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { menu = false; nav.navigate(Routes.convert("csv-pdf")) })
                        DropdownMenuItem(text = { Text("Convert to Excel") }, leadingIcon = { Icon(Icons.Outlined.GridOn, null) }, onClick = { menu = false; nav.navigate(Routes.convert("csv-xlsx")) })
                        DropdownMenuItem(text = { Text("Convert to JSON") }, leadingIcon = { Icon(Icons.Outlined.Code, null) }, onClick = { menu = false; nav.navigate(Routes.convert("csv-json")) })
                        if (f != null) DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; FileUtil.shareUri(ctx, Uri.parse(f.uri), f.mime) })
                    }
                }
            }
        },
        bottomBar = { AdSlot(vm.isPro, banner = true) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            TableStateBox(vm) { table ->
                if (raw) RawView(vm.rawText)
                else Column {
                    if (query.isNotBlank()) Text(
                        "${shown.size} matching rows", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (shown.isEmpty() && query.isNotBlank()) EmptyState(Icons.Outlined.Search, "No matches", "Nothing in this file contains \"$query\".")
                    else TableView(
                        table, shown, query, sortCol, asc, listState,
                        onHeader = { c -> if (sortCol == c) { if (asc) asc = false else sortCol = -1 } else { sortCol = c; asc = true } },
                        onHeaderLong = { colSheet = it },
                    )
                }
            }
        }
    }

    val sheetCol = colSheet
    if (sheetCol != null && t != null) {
        val stats = remember(t, sheetCol) { Analysis.column(t, sheetCol) }
        ModalBottomSheet(onDismissRequest = { colSheet = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stats.name, style = MaterialTheme.typography.titleLarge)
                Text(if (stats.numeric) "Number column" else "Text column", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ColumnStatsBody(stats)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { sortCol = sheetCol; asc = true; colSheet = null }, modifier = Modifier.weight(1f)) { Text("Sort A → Z") }
                    OutlinedButton(onClick = { sortCol = sheetCol; asc = false; colSheet = null }, modifier = Modifier.weight(1f)) { Text("Sort Z → A") }
                }
            }
        }
    }
}

@Composable
private fun RawView(text: String) {
    val lines = remember(text) { text.lines() }
    val hs = rememberScrollState()
    LazyColumn(Modifier.fillMaxSize().horizontalScroll(hs).padding(horizontal = 12.dp)) {
        items(lines.size) { i ->
            Row {
                Text("${i + 1}".padStart(4), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(lines[i], fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false)
            }
        }
    }
}

fun columnWidths(t: CsvTable): List<Dp> = (0 until t.colCount).map { c ->
    val len = maxOf(t.header[c].length, t.rows.take(300).maxOfOrNull { it.getOrElse(c) { "" }.length } ?: 0)
    (len * 8 + 28).coerceIn(80, 260).dp
}

@Composable
private fun TableView(
    t: CsvTable,
    rows: List<Pair<Int, List<String>>>,
    query: String,
    sortCol: Int,
    asc: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onHeader: (Int) -> Unit,
    onHeaderLong: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val widths = remember(t) { columnWidths(t) }
    val numW = 52.dp
    val total = widths.fold(numW) { a, b -> a + b }
    val hs = rememberScrollState()
    Box(Modifier.fillMaxSize().horizontalScroll(hs)) {
        LazyColumn(state = listState, modifier = Modifier.width(total).fillMaxHeight()) {
            stickyHeader {
                Row(Modifier.background(cs.primaryContainer).height(IntrinsicSize.Min)) {
                    Text("#", Modifier.width(numW).padding(10.dp), style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                    t.header.forEachIndexed { i, h ->
                        Row(
                            Modifier.width(widths[i]).combinedClickable(onClick = { onHeader(i) }, onLongClick = { onHeaderLong(i) }).padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(h, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (sortCol == i) Icon(if (asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.size(14.dp), tint = cs.onPrimaryContainer)
                        }
                    }
                }
            }
            items(rows.size) { idx ->
                val (orig, r) = rows[idx]
                Row(Modifier.background(if (idx % 2 == 0) cs.surface else cs.background)) {
                    Text("${orig + 1}", Modifier.width(numW).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    r.forEachIndexed { i, v ->
                        if (i < widths.size) Text(
                            highlight(v, query, cs.primary.copy(alpha = 0.25f)),
                            Modifier.width(widths[i]).padding(horizontal = 10.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = cs.outlineVariant)
            }
        }
    }
}

private fun highlight(text: String, q: String, bg: Color) = buildAnnotatedString {
    if (q.isBlank()) { append(text); return@buildAnnotatedString }
    var start = 0
    while (true) {
        val i = text.indexOf(q, start, ignoreCase = true)
        if (i < 0) { append(text.substring(start)); break }
        append(text.substring(start, i))
        withStyle(SpanStyle(background = bg, fontWeight = FontWeight.Bold)) { append(text.substring(i, i + q.length)) }
        start = i + q.length
    }
}

@Composable
private fun ColumnStatsBody(s: ColumnStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatLine("Filled", "${s.filled}")
        StatLine("Empty", "${s.empty}")
        StatLine("Unique values", "${s.unique}")
        if (s.numeric) {
            StatLine("Minimum", fmt(s.min))
            StatLine("Maximum", fmt(s.max))
            StatLine("Average", fmt(s.mean))
            StatLine("Sum", fmt(s.sum))
        } else if (s.top.isNotEmpty()) {
            Text("Most common", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.top.forEach { (v, n) -> StatLine(v.ifBlank { "(empty)" }, "$n×") }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AnalysisScreen(vm: AppViewModel, nav: NavController) {
    Scaffold(
        topBar = { AppTopBar("Data analysis", onBack = { nav.popBackStack() }, subtitle = vm.current?.name) },
        bottomBar = { AdSlot(vm.isPro, banner = true) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            TableStateBox(vm) { t ->
                val stats by produceState<List<ColumnStats>?>(null, t) {
                    value = withContext(Dispatchers.Default) { (0 until t.colCount).map { Analysis.column(t, it) } }
                }
                val summary by produceState<Pair<Int, Int>?>(null, t) {
                    value = withContext(Dispatchers.Default) { Analysis.emptyCells(t) to Analysis.duplicateRows(t) }
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Rows", "${t.rows.size}", Modifier.weight(1f))
                            StatCard("Columns", "${t.colCount}", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Empty cells", summary?.first?.toString() ?: "…", Modifier.weight(1f))
                            StatCard("Duplicate rows", summary?.second?.toString() ?: "…", Modifier.weight(1f))
                        }
                    }
                    val list = stats
                    if (list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else {
                        itemsIndexedCompat(list) { i, s ->
                            val locked = !vm.isPro && i >= 3
                            Card16 {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    AssistChip(onClick = {}, label = { Text(if (s.numeric) "Number" else "Text") })
                                }
                                if (locked) {
                                    Text("Full statistics for every column are part of Premium.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { nav.navigate(Routes.PREMIUM) }) { Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Unlock") }
                                } else ColumnStatsBody(s)
                            }
                        }
                        if (!vm.isPro && list.size > 3) item {
                            Text("Free preview: first 3 columns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item { AdSlot(vm.isPro) }
                }
            }
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(list: List<T>, content: @Composable (Int, T) -> Unit) {
    items(list.size) { i -> content(i, list[i]) }
}

private val chartColors = listOf(
    Color(0xFF16A34A), Color(0xFF2563EB), Color(0xFFF59E0B), Color(0xFFDC2626), Color(0xFF7C3AED),
    Color(0xFF0D9488), Color(0xFFDB2777), Color(0xFF65A30D), Color(0xFF0891B2), Color(0xFFEA580C),
    Color(0xFF4F46E5), Color(0xFF64748B),
)

@Composable
fun ChartsScreen(vm: AppViewModel, nav: NavController) {
    var type by rememberSaveable { mutableStateOf("Bar") }
    var labelCol by rememberSaveable { mutableIntStateOf(0) }
    var valueCol by rememberSaveable { mutableIntStateOf(-1) }
    var agg by rememberSaveable { mutableStateOf("Sum") }
    Scaffold(topBar = { AppTopBar("Charts", onBack = { nav.popBackStack() }, subtitle = vm.current?.name) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            TableStateBox(vm) { t ->
                val numericCols = remember(t) { (0 until t.colCount).filter { Analysis.column(t, it).numeric } }
                val data = remember(t, labelCol, valueCol, agg) {
                    val groups = LinkedHashMap<String, MutableList<Double>>()
                    t.rows.forEach { r ->
                        val k = r.getOrElse(labelCol) { "" }.ifBlank { "(empty)" }
                        val v = if (valueCol < 0) 1.0 else Analysis.toNumber(r.getOrElse(valueCol) { "" }) ?: return@forEach
                        groups.getOrPut(k) { ArrayList() }.add(v)
                    }
                    groups.map { (k, v) -> k to if (valueCol >= 0 && agg == "Average") v.average() else v.sum() }
                        .sortedByDescending { it.second }.take(12)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Bar", "Line", "Pie").forEach { c -> FilterChip(selected = type == c, onClick = { type = c }, label = { Text(c) }) }
                        }
                    }
                    item {
                        ColumnPicker("Group by", t.header, labelCol) { labelCol = it }
                        Spacer(Modifier.height(8.dp))
                        ColumnPicker("Value", listOf("Count rows") + numericCols.map { t.header[it] }, if (valueCol < 0) 0 else numericCols.indexOf(valueCol) + 1) {
                            valueCol = if (it == 0) -1 else numericCols[it - 1]
                        }
                        if (valueCol >= 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            listOf("Sum", "Average").forEach { a -> FilterChip(selected = agg == a, onClick = { agg = a }, label = { Text(a) }) }
                        }
                    }
                    item {
                        Card16 {
                            if (data.isEmpty()) Text("No numbers to chart in this column.")
                            else when (type) {
                                "Line" -> LineChart(data)
                                "Pie" -> PieChart(data)
                                else -> BarChart(data)
                            }
                        }
                    }
                    item {
                        Text("Showing the top ${data.size} groups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnPicker(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(options.getOrElse(selected) { "" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false }) }
        }
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Double>>) {
    val max = data.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.forEachIndexed { i, (k, v) ->
            Column {
                Row {
                    Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt(v), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(chartColors[i % chartColors.size]))
                }
            }
        }
    }
}

@Composable
private fun LineChart(data: List<Pair<String, Double>>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = data.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            for (g in 0..4) {
                val y = size.height * g / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val step = if (data.size > 1) size.width / (data.size - 1) else 0f
            val pts = data.mapIndexed { i, (_, v) -> Offset(if (data.size > 1) i * step else size.width / 2, size.height - (v / max).toFloat() * size.height * 0.95f) }
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, line, style = Stroke(width = 5f))
            pts.forEach { drawCircle(line, 7f, it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(data.joinToString("  ·  ") { it.first }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PieChart(data: List<Pair<String, Double>>) {
    val total = data.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.size(200.dp)) {
            var start = -90f
            data.forEachIndexed { i, (_, v) ->
                val sweep = (v / total * 360).toFloat()
                drawArc(chartColors[i % chartColors.size], start, sweep, useCenter = true, size = Size(size.width, size.height))
                start += sweep
            }
        }
        Spacer(Modifier.height(12.dp))
        data.forEachIndexed { i, (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(chartColors[i % chartColors.size]))
                Spacer(Modifier.width(8.dp))
                Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(String.format(Locale.US, "%.1f%%", v / total * 100), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
