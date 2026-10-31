@file:OptIn(ExperimentalMaterial3Api::class)

package com.csvviewer.fileviewer.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.csvviewer.fileviewer.AppViewModel
import com.csvviewer.fileviewer.data.Converters
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.FileUtil
import com.csvviewer.fileviewer.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

@Composable
private fun ShareAction(vm: AppViewModel) {
    val ctx = LocalContext.current
    val f = vm.current ?: return
    IconButton(onClick = { FileUtil.shareUri(ctx, Uri.parse(f.uri), f.mime) }) { Icon(Icons.Outlined.Share, "Share") }
}

/** Keeps one PdfRenderer open while the screen is visible; pages render one at a time. */
private class PdfHolder(val pfd: ParcelFileDescriptor) {
    val renderer = PdfRenderer(pfd)
    val lock = Mutex()
    fun close() { runCatching { renderer.close(); pfd.close() } }
}

@Composable
fun PdfViewerScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val f = vm.current
    val holder = remember(f?.uri) {
        runCatching { f?.let { ctx.contentResolver.openFileDescriptor(Uri.parse(it.uri), "r") }?.let(::PdfHolder) }.getOrNull()
    }
    DisposableEffect(holder) { onDispose { holder?.close() } }
    val listState = rememberLazyListState()
    val count = holder?.renderer?.pageCount ?: 0
    Scaffold(
        topBar = { AppTopBar(f?.name ?: "PDF", onBack = { nav.popBackStack() }, subtitle = if (count > 0) "Page ${listState.firstVisibleItemIndex + 1} of $count" else null) { ShareAction(vm) } },
        bottomBar = { AdSlot(vm.isPro, banner = true) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (holder == null) EmptyState(Icons.Outlined.PictureAsPdf, "Couldn't open this PDF", "The file may be damaged or password-protected.")
            else BoxWithConstraints {
                val widthPx = constraints.maxWidth
                LazyColumn(state = listState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count) { i ->
                        val bmp by produceState<Bitmap?>(null, i) {
                            value = withContext(Dispatchers.IO) {
                                holder.lock.withLock {
                                    runCatching {
                                        holder.renderer.openPage(i).use { page ->
                                            val w = widthPx
                                            val h = (w.toFloat() / page.width * page.height).toInt()
                                            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            b.eraseColor(android.graphics.Color.WHITE)
                                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            b
                                        }
                                    }.getOrNull()
                                }
                            }
                        }
                        val b = bmp
                        if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        else Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
fun TextViewerScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val f = vm.current
    var size by remember { mutableFloatStateOf(14f) }
    var mono by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(true) }
    val text by produceState<String?>(null, f?.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(f!!.uri))?.use { s ->
                    val bytes = s.readBytes()
                    val limit = 2_000_000
                    val str = String(bytes, 0, minOf(bytes.size, limit), Charsets.UTF_8)
                    if (bytes.size > limit) "$str\n\n… (file truncated at 2 MB)" else str
                }
            }.getOrNull() ?: ""
        }
    }
    Scaffold(
        topBar = {
            AppTopBar(f?.name ?: "Text", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { if (size > 10) size -= 2 }) { Icon(Icons.Outlined.ZoomOut, "Smaller text") }
                IconButton(onClick = { if (size < 28) size += 2 }) { Icon(Icons.Outlined.ZoomIn, "Larger text") }
                IconButton(onClick = { mono = !mono }) { Icon(Icons.Outlined.TextFields, "Toggle monospace") }
                IconButton(onClick = { wrap = !wrap }) { Icon(Icons.Outlined.WrapText, "Toggle wrap") }
                ShareAction(vm)
            }
        },
        bottomBar = { AdSlot(vm.isPro, banner = true) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val t = text
            if (t == null) LoadingBox()
            else {
                val lines = remember(t) { t.lines() }
                val hs = rememberScrollState()
                LazyColumn(
                    (if (wrap) Modifier else Modifier.horizontalScroll(hs)).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(lines.size) { i ->
                        Text(lines[i], fontSize = size.sp, lineHeight = (size * 1.45f).sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, softWrap = wrap)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewerScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val f = vm.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val bmp by produceState<Bitmap?>(null, f?.uri) {
        value = withContext(Dispatchers.IO) { runCatching { Converters.decodeSampled(ctx, Uri.parse(f!!.uri), 2400) }.getOrNull() }
    }
    Scaffold(topBar = { AppTopBar(f?.name ?: "Image", onBack = { nav.popBackStack() }) { ShareAction(vm) } }) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val b = bmp
            if (b == null) CircularProgressIndicator()
            else Image(
                b.asImageBitmap(), f?.name,
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private data class ZipEntryInfo(val name: String, val size: Long, val dir: Boolean)

@Composable
fun ZipViewerScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var zipUri by remember { mutableStateOf(vm.current?.takeIf { it.kind == FileKind.ZIP }?.let { Uri.parse(it.uri) to it.name }) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { val item = vm.register(uri); zipUri = uri to item.name }
    }
    val entries by produceState<List<ZipEntryInfo>?>(null, zipUri) {
        val z = zipUri
        value = if (z == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val list = ArrayList<ZipEntryInfo>()
                ctx.contentResolver.openInputStream(z.first)?.use { s ->
                    ZipInputStream(s).use { zs ->
                        var e = zs.nextEntry
                        while (e != null) {
                            var size = e.size
                            if (size < 0 && !e.isDirectory) { size = 0; val buf = ByteArray(8192); while (true) { val n = zs.read(buf); if (n < 0) break; size += n } }
                            list.add(ZipEntryInfo(e.name, size, e.isDirectory))
                            e = zs.nextEntry
                        }
                    }
                }
                list
            }.getOrElse { emptyList() }
        }
    }

    // Extract one entry to the cache and open it in the matching viewer
    fun openEntry(name: String) {
        val z = zipUri ?: return
        try {
            val dir = File(ctx.cacheDir, "zipview").apply { mkdirs() }
            val out = File(dir, name.substringAfterLast('/'))
            ctx.contentResolver.openInputStream(z.first)?.use { s ->
                ZipInputStream(s).use { zs ->
                    var e = zs.nextEntry
                    while (e != null) {
                        if (e.name == name) { out.outputStream().use { zs.copyTo(it) }; break }
                        e = zs.nextEntry
                    }
                }
            }
            nav.openFile(vm, vm.registerLocal(out, FileUtil.uriFor(ctx, out)))
        } catch (ex: Exception) {
            ctx.toast(ex.message ?: "Couldn't open entry")
        }
    }

    Scaffold(
        topBar = { AppTopBar(zipUri?.second ?: "ZIP viewer", onBack = { nav.popBackStack() }, subtitle = entries?.let { "${it.count { e -> !e.dir }} files" }) },
        bottomBar = { AdSlot(vm.isPro, banner = true) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val list = entries
            when {
                zipUri == null -> EmptyState(Icons.Outlined.Archive, "Open a ZIP file", "See the files inside an archive and open them without extracting everything.", "Choose ZIP") { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Outlined.Archive, "Empty or unreadable", "This archive has no readable files. RAR and 7z aren't supported yet.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(list) { e ->
                        val kind = FileKind.of(e.name)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (e.dir) Icon(Icons.Outlined.Folder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            else FileIconBox(kind, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!e.dir) Text(FileUtil.size(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!e.dir && kind != FileKind.OTHER && kind != FileKind.ZIP) TextButton(onClick = { openEntry(e.name) }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}
