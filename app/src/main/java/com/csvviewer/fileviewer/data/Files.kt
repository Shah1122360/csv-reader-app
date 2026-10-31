package com.csvviewer.fileviewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class FileKind(val label: String) {
    CSV("CSV"), TSV("TSV"), EXCEL("Excel"), JSON("JSON"), PDF("PDF"),
    TEXT("Text"), IMAGE("Image"), ZIP("ZIP"), OTHER("File");

    val isTable get() = this == CSV || this == TSV || this == EXCEL || this == JSON

    companion object {
        fun of(name: String, mime: String = ""): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when {
                ext == "csv" -> CSV
                ext == "tsv" || ext == "tab" -> TSV
                ext == "xlsx" || ext == "xls" -> EXCEL
                ext == "json" -> JSON
                ext == "pdf" -> PDF
                ext in listOf("txt", "log", "md", "xml", "html", "htm") -> TEXT
                ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> IMAGE
                ext == "zip" -> ZIP
                mime.contains("csv") -> CSV
                mime == "application/pdf" -> PDF
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("text/") -> TEXT
                else -> OTHER
            }
        }
    }
}

data class FileItem(
    val uri: String,
    val name: String,
    val size: Long,
    val mime: String,
    val lastOpened: Long,
    val favorite: Boolean = false,
    val trashed: Boolean = false,
    val trashedAt: Long = 0,
    val progress: Float = 0f,
    val lastRow: Int = 0,
) {
    val kind get() = FileKind.of(name, mime)

    fun toJson(): JSONObject = JSONObject()
        .put("uri", uri).put("name", name).put("size", size).put("mime", mime)
        .put("lastOpened", lastOpened).put("favorite", favorite).put("trashed", trashed)
        .put("trashedAt", trashedAt).put("progress", progress.toDouble()).put("lastRow", lastRow)

    companion object {
        fun fromJson(o: JSONObject) = FileItem(
            uri = o.getString("uri"), name = o.optString("name"), size = o.optLong("size"),
            mime = o.optString("mime"), lastOpened = o.optLong("lastOpened"),
            favorite = o.optBoolean("favorite"), trashed = o.optBoolean("trashed"),
            trashedAt = o.optLong("trashedAt"), progress = o.optDouble("progress", 0.0).toFloat(),
            lastRow = o.optInt("lastRow"),
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("csv_reader", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme", v.name).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var isPro: Boolean
        get() = sp.getBoolean("pro", false)
        set(v) = sp.edit().putBoolean("pro", v).apply()

    var language: String
        get() = sp.getString("language", "English")!!
        set(v) = sp.edit().putString("language", v).apply()

    var opens: Int
        get() = sp.getInt("opens", 0)
        set(v) = sp.edit().putInt("opens", v).apply()

    fun loadFiles(): List<FileItem> = runCatching {
        val arr = JSONArray(sp.getString("files", "[]"))
        (0 until arr.length()).map { FileItem.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveFiles(files: List<FileItem>) {
        val arr = JSONArray()
        files.forEach { arr.put(it.toJson()) }
        sp.edit().putString("files", arr.toString()).apply()
    }
}

object FileUtil {
    fun exportsDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    fun newExport(context: Context, baseName: String, ext: String): File {
        val clean = baseName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "file" }
        return File(exportsDir(context), "$clean.$ext")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun share(context: Context, file: File, mime: String) = shareUri(context, uriFor(context, file), mime)

    fun shareUri(context: Context, uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share file"))
    }

    fun copyTo(context: Context, file: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    fun dirSize(f: File): Long = if (f.isDirectory) (f.listFiles() ?: emptyArray()).sumOf { dirSize(it) } else f.length()

    fun size(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024 / 1024)
    }

    fun time(ms: Long): String {
        if (ms <= 0) return ""
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000} d ago"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
        }
    }
}
