@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.csvviewer.fileviewer.ui.screens

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.Converters
import com.csvviewer.fileviewer.data.CsvTable
import com.csvviewer.fileviewer.data.CsvWriter
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.FileUtil
import com.csvviewer.fileviewer.data.OutFormat
import com.csvviewer.fileviewer.ui.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun displayName(ctx: android.content.Context, uri: Uri): String {
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
    } catch (_: Exception) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

/** Card shown after a conversion: save a copy, share, or open the result. */
@Composable
fun ResultCard(file: File, mime: String, onOpen: (() -> Unit)?) {
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) {
            runCatching { FileUtil.copyTo(ctx, file, uri) }
                .onSuccess { ctx.toast("Saved") }
                .onFailure { ctx.toast(it.message ?: "Save failed") }
        }
    }
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigCheckSmall()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
                Text("${file.name} · ${FileUtil.size(file.length())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { saver.launch(file.name) }, modifier = Modifier.weight(1f)) { Text("Save") }
            OutlinedButton(onClick = { FileUtil.share(ctx, file, mime) }, modifier = Modifier.weight(1f)) { Text("Share") }
            if (onOpen != null) OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
        }
    }
}

@Composable
private fun BigCheckSmall() {
    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
}

@Composable
private fun PickCard(title: String, picked: String?, onPick: () -> Unit) {
    Card16 {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (picked != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconBox(FileKind.of(picked), 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(picked, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onPick) { Text("Change") }
            }
        } else OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose file")
        }
    }
}

private fun accepts(id: String, kind: FileKind) = when (id) {
    "xlsx-csv" -> kind == FileKind.EXCEL
    "json-csv" -> kind == FileKind.JSON
    "tsv-csv" -> kind == FileKind.TSV || kind == FileKind.TEXT || kind == FileKind.CSV
    "img-pdf" -> false
    else -> kind.isTable
}

private fun outFor(id: String) = when (id) {
    "csv-pdf", "img-pdf" -> OutFormat.PDF
    "csv-xlsx" -> OutFormat.XLSX
    "csv-json" -> OutFormat.JSON
    "csv-html" -> OutFormat.HTML
    "csv-tsv" -> OutFormat.TSV
    else -> OutFormat.CSV
}

@Composable
fun ConvertScreen(vm: AppViewModel, nav: NavController, id: String) {
    val tool = convertTools.find { it.id == id } ?: convertTools[0]
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val out = outFor(id)
    val cur = vm.current
    var input by remember { mutableStateOf(if (cur != null && accepts(id, cur.kind)) Uri.parse(cur.uri) to cur.name else null) }
    val images = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<File?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { input = uri to displayName(ctx, uri); result = null; err = null }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        images.addAll(uris); result = null
    }

    fun convert() = scope.launch {
        busy = true; err = null; result = null
        try {
            result = withContext(Dispatchers.IO) {
                val baseName = if (id == "img-pdf") "images_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) else input!!.second
                val file = FileUtil.newExport(ctx, baseName, out.ext)
                file.outputStream().use { os ->
                    if (id == "img-pdf") Converters.imagesToPdf(ctx, images.toList(), os)
                    else {
                        val (t, _, _) = vm.readTable(input!!.first, input!!.second)
                        Converters.write(t, out, input!!.second, os)
                    }
                }
                file
            }
        } catch (e: Exception) {
            err = e.message ?: "Conversion failed"
        }
        busy = false
    }

    Scaffold(topBar = { AppTopBar(tool.title, onBack = { nav.popBackStack() }) }, bottomBar = { AdSlot(vm.isPro, banner = true) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(tool.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (id == "img-pdf") {
                item {
                    Card16 {
                        Text("Images (${images.size})", style = MaterialTheme.typography.titleSmall)
                        images.forEachIndexed { i, u ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}. ${displayName(ctx, u)}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { images.removeAt(i) }) { Icon(Icons.Outlined.Close, "Remove") }
                            }
                        }
                        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add images")
                        }
                    }
                }
            } else item { PickCard("Input file", input?.second) { picker.launch(arrayOf("*/*")) } }
            item {
                Card16 {
                    Text("Output", style = MaterialTheme.typography.titleSmall)
                    Text("${out.label} (.${out.ext})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(
                    onClick = { convert() },
                    enabled = !busy && (if (id == "img-pdf") images.isNotEmpty() else input != null),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (busy) "Converting…" else "Convert") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            result?.let { f ->
                item {
                    ResultCard(f, out.mime, onOpen = {
                        nav.openFile(vm, vm.registerLocal(f, FileUtil.uriFor(ctx, f)))
                    })
                }
            }
            item { AdSlot(vm.isPro) }
        }
    }
}

private fun zipFiles(ctx: android.content.Context, files: List<Pair<String, ByteArray>>, zip: File) {
    ZipOutputStream(zip.outputStream()).use { z ->
        val used = HashSet<String>()
        files.forEach { (name, bytes) ->
            var n = name
            var k = 1
            while (!used.add(n)) { n = name.substringBeforeLast('.') + " ($k)." + name.substringAfterLast('.', "bin"); k++ }
            z.putNextEntry(ZipEntry(n)); z.write(bytes); z.closeEntry()
        }
    }
}

@Composable
fun BatchScreen(vm: AppViewModel, nav: NavController) {
    if (!vm.isPro) {
        LaunchedEffect(Unit) { nav.navigate(Routes.PREMIUM) { popUpTo(Routes.BATCH) { inclusive = true } } }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputs = remember { mutableStateListOf<Uri>() }
    var format by remember { mutableStateOf(OutFormat.PDF) }
    var progress by remember { mutableIntStateOf(-1) }
    val status = remember { mutableStateMapOf<Uri, String>() }
    var result by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> inputs.addAll(uris.filter { it !in inputs }); result = null }

    Scaffold(topBar = { AppTopBar("Batch convert", onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Convert several CSV, Excel or JSON files to one format. Results are packed into a single ZIP.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text("Convert to", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OutFormat.PDF, OutFormat.XLSX, OutFormat.JSON, OutFormat.CSV, OutFormat.HTML).forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add files (${inputs.size})")
                }
            }
            items(inputs.toList()) { u ->
                val name = remember(u) { displayName(ctx, u) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileIconBox(FileKind.of(name), 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        status[u]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it == "Done") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                    }
                    IconButton(onClick = { inputs.remove(u) }, enabled = progress < 0) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            item {
                Button(
                    enabled = inputs.isNotEmpty() && progress < 0,
                    onClick = {
                        scope.launch {
                            status.clear(); result = null
                            val outs = ArrayList<Pair<String, ByteArray>>()
                            inputs.toList().forEachIndexed { i, u ->
                                progress = i
                                val name = displayName(ctx, u)
                                try {
                                    val bytes = withContext(Dispatchers.IO) {
                                        val (t, _, _) = vm.readTable(u, name)
                                        val bo = java.io.ByteArrayOutputStream()
                                        Converters.write(t, format, name, bo)
                                        bo.toByteArray()
                                    }
                                    outs.add(name.substringBeforeLast('.') + "." + format.ext to bytes)
                                    status[u] = "Done"
                                } catch (e: Exception) {
                                    status[u] = "Failed: ${e.message ?: "unsupported file"}"
                                }
                            }
                            if (outs.isNotEmpty()) {
                                val zip = FileUtil.newExport(ctx, "converted_files", "zip")
                                withContext(Dispatchers.IO) { zipFiles(ctx, outs, zip) }
                                result = zip
                            }
                            progress = -1
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (progress >= 0) "Converting ${progress + 1} of ${inputs.size}…" else "Convert all") }
                if (progress >= 0) LinearProgressIndicator(progress = { (progress + 1f) / inputs.size }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            result?.let { f -> item { ResultCard(f, "application/zip", null) } }
        }
    }
}

@Composable
fun MergeScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputs = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<File?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> inputs.addAll(uris.filter { it !in inputs }); result = null }

    Scaffold(topBar = { AppTopBar("Merge CSV", onBack = { nav.popBackStack() }) }, bottomBar = { AdSlot(vm.isPro, banner = true) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Rows from every file are combined under one header. Columns are matched by name.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add CSV files (${inputs.size})")
                }
            }
            items(inputs.toList()) { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName(ctx, u), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { inputs.remove(u) }) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            item {
                Button(
                    enabled = inputs.size >= 2 && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val merged = withContext(Dispatchers.IO) {
                                    val tables = inputs.map { u -> vm.readTable(u, displayName(ctx, u)).first }
                                    val header = LinkedHashSet<String>()
                                    tables.forEach { header.addAll(it.header) }
                                    val h = header.toList()
                                    val rows = tables.flatMap { t ->
                                        val idx = h.map { t.header.indexOf(it) }
                                        t.rows.map { r -> idx.map { if (it >= 0) r.getOrElse(it) { "" } else "" } }
                                    }
                                    CsvTable(h, rows)
                                }
                                val f = FileUtil.newExport(ctx, "merged", "csv")
                                withContext(Dispatchers.IO) { f.writeText(CsvWriter.write(merged)) }
                                info = "${merged.rows.size} rows · ${merged.colCount} columns"
                                result = f
                            } catch (e: Exception) {
                                info = e.message ?: "Merge failed"
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (busy) "Merging…" else "Merge") }
                if (inputs.size < 2) Text("Add at least 2 files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info?.let { item { Text(it) } }
            result?.let { f -> item { ResultCard(f, "text/csv") { nav.openFile(vm, vm.registerLocal(f, FileUtil.uriFor(ctx, f))) } } }
        }
    }
}

@Composable
fun CompressScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputs = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<File?>(null) }
    var before by remember { mutableStateOf(0L) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> inputs.addAll(uris.filter { it !in inputs }); result = null }

    Scaffold(topBar = { AppTopBar("Compress to ZIP", onBack = { nav.popBackStack() }) }, bottomBar = { AdSlot(vm.isPro, banner = true) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add files (${inputs.size})")
                }
            }
            items(inputs.toList()) { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName(ctx, u), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { inputs.remove(u) }) { Icon(Icons.Outlined.Close, "Remove") }
                }
            }
            item {
                Button(
                    enabled = inputs.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val files = withContext(Dispatchers.IO) {
                                    inputs.map { u -> displayName(ctx, u) to (ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: ByteArray(0)) }
                                }
                                before = files.sumOf { it.second.size.toLong() }
                                val zip = FileUtil.newExport(ctx, "archive_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()), "zip")
                                withContext(Dispatchers.IO) { zipFiles(ctx, files, zip) }
                                result = zip
                            } catch (e: Exception) {
                                ctx.toast(e.message ?: "Compression failed")
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (busy) "Compressing…" else "Compress") }
            }
            result?.let { f ->
                item { Text("Original ${FileUtil.size(before)} → ZIP ${FileUtil.size(f.length())}") }
                item { ResultCard(f, "application/zip", null) }
            }
        }
    }
}

@Composable
fun ScannerScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var err by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pdf
            if (pdf != null) {
                try {
                    val dir = File(ctx.filesDir, "scans").apply { mkdirs() }
                    val f = File(dir, "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf")
                    ctx.contentResolver.openInputStream(pdf.uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                    val item = vm.registerLocal(f, FileUtil.uriFor(ctx, f))
                    nav.popBackStack()
                    nav.openFile(vm, item)
                } catch (e: Exception) {
                    err = e.message
                }
            }
        }
    }
    fun start() {
        val activity = ctx as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { err = it.message ?: "Scanner isn't available on this phone" }
    }
    Scaffold(topBar = { AppTopBar("Document scanner", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                Icons.Outlined.CameraAlt, "Scan paper to PDF",
                "Point the camera at a page. Edges are found automatically, and you can add more pages before saving.",
                "Start scanning",
            ) { start() }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            AdSlot(vm.isPro)
        }
    }
}
