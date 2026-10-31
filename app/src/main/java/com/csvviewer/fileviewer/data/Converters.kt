package com.csvviewer.fileviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

enum class OutFormat(val label: String, val ext: String, val mime: String) {
    CSV("CSV", "csv", "text/csv"),
    XLSX("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("PDF", "pdf", "application/pdf"),
    JSON("JSON", "json", "application/json"),
    HTML("HTML", "html", "text/html"),
    TSV("TSV", "tsv", "text/tab-separated-values"),
}

object Converters {

    fun write(t: CsvTable, format: OutFormat, title: String, out: OutputStream) {
        when (format) {
            OutFormat.CSV -> out.write(CsvWriter.write(t, ',').toByteArray())
            OutFormat.TSV -> out.write(CsvWriter.write(t, '\t').toByteArray())
            OutFormat.JSON -> out.write(toJson(t).toByteArray())
            OutFormat.HTML -> out.write(toHtml(t, title).toByteArray())
            OutFormat.PDF -> toPdf(t, title, out)
            OutFormat.XLSX -> toXlsx(t, out)
        }
    }

    fun toJson(t: CsvTable): String {
        val arr = JSONArray()
        t.rows.forEach { r ->
            val o = JSONObject()
            t.header.forEachIndexed { i, h -> o.put(h, r.getOrElse(i) { "" }) }
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun toHtml(t: CsvTable, title: String): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\"><title>").append(esc(title)).append("</title>")
        append("<style>body{font-family:sans-serif}table{border-collapse:collapse}th,td{border:1px solid #d9e0dc;padding:4px 8px}th{background:#dcfce7}</style>")
        append("</head><body><table><thead><tr>")
        t.header.forEach { append("<th>").append(esc(it)).append("</th>") }
        append("</tr></thead><tbody>")
        t.rows.forEach { r ->
            append("<tr>")
            r.forEach { append("<td>").append(esc(it)).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table></body></html>")
    }

    fun toPdf(t: CsvTable, title: String, out: OutputStream) {
        val doc = PdfDocument()
        val pageW = 842
        val pageH = 595
        val margin = 28f
        val rowH = 16f
        val text = Paint().apply { textSize = 8.5f; isAntiAlias = true; color = 0xFF111814.toInt() }
        val bold = Paint(text).apply { isFakeBoldText = true }
        val titlePaint = Paint(bold).apply { textSize = 13f }
        val headBg = Paint().apply { color = 0xFFDCFCE7.toInt() }
        val line = Paint().apply { color = 0xFFD9E0DC.toInt(); strokeWidth = 0.6f }

        val cols = max(1, t.colCount)
        val sample = listOf(t.header) + t.rows.take(200)
        val lens = (0 until cols).map { c -> sample.maxOf { it.getOrElse(c) { "" }.length }.coerceIn(3, 40) }
        val total = lens.sum().toFloat()
        val widths = lens.map { it / total * (pageW - margin * 2) }

        fun fit(s: String, w: Float, p: Paint): String {
            val str = s.replace('\n', ' ').take(80)
            if (p.measureText(str) <= w - 6) return str
            var e = str
            while (e.isNotEmpty() && p.measureText("$e…") > w - 6) e = e.dropLast(1)
            return "$e…"
        }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun drawRow(r: List<String>, header: Boolean) {
            val c = page!!.canvas
            if (header) c.drawRect(margin, y, pageW - margin, y + rowH, headBg)
            var x = margin
            for (i in 0 until cols) {
                c.drawText(fit(r.getOrElse(i) { "" }, widths[i], if (header) bold else text), x + 3, y + rowH - 4.5f, if (header) bold else text)
                x += widths[i]
            }
            c.drawLine(margin, y + rowH, pageW - margin, y + rowH, line)
            y += rowH
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page!!.canvas.drawText(fit(title, pageW - margin * 2, titlePaint), margin, margin + 6, titlePaint)
            y = margin + 16
            drawRow(t.header, true)
        }

        newPage()
        for (r in t.rows) {
            if (y + rowH > pageH - margin) newPage()
            drawRow(r, false)
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    /** Minimal valid .xlsx: one sheet, inline strings, numbers kept as numbers. */
    fun toXlsx(t: CsvTable, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, body: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
        }
        entry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
        )
        entry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        )
        entry(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        )
        entry(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
        )
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        (listOf(t.header) + t.rows).forEachIndexed { ri, r ->
            sheet.append("<row r=\"").append(ri + 1).append("\">")
            r.forEachIndexed { ci, v ->
                val ref = colName(ci) + (ri + 1)
                val tv = v.trim()
                // Keep leading-zero codes like "007" as text
                val isNumber = ri > 0 && tv.toDoubleOrNull() != null &&
                    (tv == "0" || !tv.startsWith("0") || tv.startsWith("0."))
                if (isNumber) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(v.trim()).append("</v></c>")
                } else if (v.isNotEmpty()) {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>")
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        entry("xl/worksheets/sheet1.xml", sheet.toString())
        zip.finish()
    }

    fun readXlsx(input: InputStream): CsvTable {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (n == "xl/sharedStrings.xml" || n.startsWith("xl/worksheets/sheet")) entries[n] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = ArrayList<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val p = Xml.newPullParser()
            p.setInput(ByteArrayInputStream(bytes), "UTF-8")
            val sb = StringBuilder()
            var inT = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "si" -> sb.setLength(0)
                        "t" -> inT = true
                    }
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "t" -> inT = false
                        "si" -> shared.add(sb.toString())
                    }
                }
                ev = p.next()
            }
        }
        val sheetName = entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            ?: throw IllegalStateException("No sheet found in this workbook")
        val rows = ArrayList<MutableList<String>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(entries[sheetName]!!), "UTF-8")
        var cur: MutableList<String>? = null
        var type: String? = null
        var col = 0
        var inV = false
        val sb = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> cur = ArrayList()
                    "c" -> {
                        type = p.getAttributeValue(null, "t")
                        col = p.getAttributeValue(null, "r")?.let(::colIndex) ?: (cur?.size ?: 0)
                        sb.setLength(0)
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> cur?.let { r ->
                        val raw = sb.toString()
                        val v = when (type) {
                            "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw.removeSuffix(".0")
                        }
                        while (r.size < col) r.add("")
                        if (r.size == col) r.add(v) else r[col] = v
                    }
                    "row" -> cur?.let { rows.add(it) }
                }
            }
            ev = p.next()
        }
        return CsvTable.from(rows)
    }

    fun jsonToTable(text: String): CsvTable {
        val trimmed = text.trim()
        val arr: JSONArray = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
            val o = JSONObject(trimmed)
            val key = o.keys().asSequence().firstOrNull { o.opt(it) is JSONArray }
            if (key != null) o.getJSONArray(key) else JSONArray().put(o)
        }
        val header = LinkedHashSet<String>()
        var arrays = false
        for (i in 0 until arr.length()) {
            when (val v = arr.opt(i)) {
                is JSONObject -> v.keys().forEach { header.add(it) }
                is JSONArray -> arrays = true
            }
        }
        fun str(v: Any?) = if (v == null || v == JSONObject.NULL) "" else v.toString()
        if (arrays && header.isEmpty()) {
            val rows = (0 until arr.length()).map { i ->
                val a = arr.optJSONArray(i) ?: JSONArray()
                (0 until a.length()).map { str(a.opt(it)) }
            }
            return CsvTable.from(rows)
        }
        if (header.isEmpty()) header.add("value")
        val rows = (0 until arr.length()).map { i ->
            val v = arr.opt(i)
            if (v is JSONObject) header.map { str(v.opt(it)) } else listOf(str(v)) + List(header.size - 1) { "" }
        }
        return CsvTable(header.toList(), rows)
    }

    fun imagesToPdf(context: Context, uris: List<Uri>, out: OutputStream) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        uris.forEachIndexed { i, uri ->
            val bmp = decodeSampled(context, uri, 1600) ?: return@forEachIndexed
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
            val scale = min((pageW - 40f) / bmp.width, (pageH - 40f) / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val left = (pageW - w) / 2
            val top = (pageH - h) / 2
            page.canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page)
            bmp.recycle()
        }
        doc.writeTo(out)
        doc.close()
    }

    fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
