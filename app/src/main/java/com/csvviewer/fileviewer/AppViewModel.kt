package com.csvviewer.fileviewer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.csvviewer.fileviewer.data.Converters
import com.csvviewer.fileviewer.data.CsvParser
import com.csvviewer.fileviewer.data.CsvTable
import com.csvviewer.fileviewer.data.CsvWriter
import com.csvviewer.fileviewer.data.FileItem
import com.csvviewer.fileviewer.data.FileKind
import com.csvviewer.fileviewer.data.Prefs
import com.csvviewer.fileviewer.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val cr get() = getApplication<Application>().contentResolver

    var themeMode by mutableStateOf(prefs.themeMode); private set
    var isPro by mutableStateOf(prefs.isPro); private set
    var onboarded by mutableStateOf(prefs.onboarded); private set
    var language by mutableStateOf(prefs.language); private set
    var files by mutableStateOf(prefs.loadFiles()); private set

    // The file open in the viewer and its parsed table
    var current by mutableStateOf<FileItem?>(null); private set
    var table by mutableStateOf<CsvTable?>(null); private set
    var rawText by mutableStateOf(""); private set
    var delimiter by mutableStateOf(','); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** Table being edited; [editorIsNew] means it came from "Create CSV" (free feature). */
    var editorIsNew by mutableStateOf(false); private set
    var editorName by mutableStateOf("new_file.csv"); private set

    /** Shown once on the 3rd launch, then never again. */
    val shouldAskRating: Boolean get() = prefs.opens == 3

    init {
        prefs.opens = prefs.opens + 1
    }

    val visibleFiles get() = files.filter { !it.trashed }
    val trash get() = files.filter { it.trashed }

    fun setTheme(m: ThemeMode) { themeMode = m; prefs.themeMode = m }
    fun updatePro(v: Boolean) { isPro = v; prefs.isPro = v }
    fun finishOnboarding() { onboarded = true; prefs.onboarded = true }
    fun resetOnboarding() { onboarded = false; prefs.onboarded = false }
    fun updateLanguage(l: String) { language = l; prefs.language = l }

    private fun saveFiles(list: List<FileItem>) { files = list; prefs.saveFiles(list) }

    /** Adds the picked file to recents (or moves it to the top) and returns it. */
    fun register(uri: Uri): FileItem {
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {
            try { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = 0L
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {}
        val mime = runCatching { cr.getType(uri) }.getOrNull() ?: ""
        val existing = files.find { it.uri == uri.toString() }
        val item = (existing ?: FileItem(uri.toString(), name, size, mime, 0))
            .copy(name = name, size = size, mime = mime, lastOpened = System.currentTimeMillis(), trashed = false)
        saveFiles(listOf(item) + files.filter { it.uri != item.uri })
        return item
    }

    fun registerLocal(file: File, uri: Uri): FileItem {
        val item = FileItem(uri.toString(), file.name, file.length(), "", System.currentTimeMillis())
        saveFiles(listOf(item) + files.filter { it.uri != item.uri })
        return item
    }

    fun toggleFavorite(item: FileItem) = update(item.uri) { it.copy(favorite = !it.favorite) }
    fun moveToTrash(item: FileItem) = update(item.uri) { it.copy(trashed = true, trashedAt = System.currentTimeMillis()) }
    fun restore(item: FileItem) = update(item.uri) { it.copy(trashed = false) }
    fun removeForever(item: FileItem) = saveFiles(files.filter { it.uri != item.uri })
    fun emptyTrash() = saveFiles(files.filter { !it.trashed })
    fun clearRecents() = saveFiles(files.filter { it.favorite || it.trashed })

    fun saveProgress(row: Int) {
        val c = current ?: return
        val total = table?.rows?.size ?: 0
        val p = if (total > 0) (row + 1f) / total else 0f
        update(c.uri) { it.copy(lastRow = row, progress = p.coerceIn(0f, 1f)) }
    }

    private fun update(uri: String, f: (FileItem) -> FileItem) {
        saveFiles(files.map { if (it.uri == uri) f(it) else it })
        current?.let { c -> if (c.uri == uri) current = files.find { it.uri == uri } }
    }

    fun fileByUri(uri: String) = files.find { it.uri == uri }

    /** Opens a file: loads the table when it is CSV/Excel/JSON. */
    fun open(item: FileItem) {
        current = item
        if (item.kind.isTable) load(item)
    }

    private fun load(item: FileItem) {
        loading = true
        error = null
        table = null
        viewModelScope.launch {
            try {
                val (t, raw, d) = withContext(Dispatchers.IO) { readTable(Uri.parse(item.uri), item.name) }
                table = t
                rawText = raw
                delimiter = d
            } catch (e: Exception) {
                error = e.message ?: "Couldn't open this file"
            } finally {
                loading = false
            }
        }
    }

    /** Reads any supported tabular file. Returns the table, the raw text (for Raw view) and the delimiter. */
    fun readTable(uri: Uri, name: String): Triple<CsvTable, String, Char> {
        val kind = FileKind.of(name)
        if (kind == FileKind.EXCEL) {
            if (name.lowercase().endsWith(".xls")) throw IllegalArgumentException("Old .xls files aren't supported yet. Save it as .xlsx or CSV.")
            val t = cr.openInputStream(uri)?.use { Converters.readXlsx(it) } ?: throw IllegalStateException("Can't read file")
            return Triple(t, CsvWriter.write(t), ',')
        }
        val text = cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IllegalStateException("Can't read file")
        if (kind == FileKind.JSON) return Triple(Converters.jsonToTable(text), text, ',')
        val d = if (kind == FileKind.TSV) '\t' else CsvParser.detectDelimiter(text)
        return Triple(CsvParser.parseTable(text, d), text, d)
    }

    fun startEditor(newTable: CsvTable?, name: String) {
        if (newTable != null) {
            table = newTable
            current = null
        }
        editorIsNew = newTable != null
        editorName = name
    }

    /** Writes the edited table back. Returns an error message or null. */
    suspend fun saveTable(t: CsvTable, target: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val d = if (current?.kind == FileKind.TSV) '\t' else delimiter
            cr.openOutputStream(target, "wt")?.use { it.write(CsvWriter.write(t, d).toByteArray()) }
                ?: return@withContext "Can't write to this file"
            withContext(Dispatchers.Main) {
                table = t
                rawText = CsvWriter.write(t, d)
            }
            null
        } catch (e: Exception) {
            e.message ?: "Save failed"
        }
    }
}
