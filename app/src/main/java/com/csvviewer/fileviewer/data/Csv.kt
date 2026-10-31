package com.csvviewer.fileviewer.data

/** A parsed table: first row is the header, every row is padded to the header width. */
data class CsvTable(val header: List<String>, val rows: List<List<String>>) {
    val colCount: Int get() = header.size

    companion object {
        fun from(all: List<List<String>>): CsvTable {
            if (all.isEmpty()) return CsvTable(listOf("Column 1"), emptyList())
            val n = all.maxOf { it.size }.coerceAtLeast(1)
            fun pad(r: List<String>) = if (r.size == n) r else r + List(n - r.size) { "" }
            val header = pad(all[0]).mapIndexed { i, s -> s.ifBlank { "Column ${i + 1}" } }
            return CsvTable(header, all.drop(1).map(::pad))
        }
    }
}

object CsvParser {
    private val candidates = listOf(',', ';', '\t', '|')

    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(5).joinToString("\n")
        val best = candidates.maxByOrNull { countOutsideQuotes(sample, it) } ?: ','
        return if (countOutsideQuotes(sample, best) > 0) best else ','
    }

    private fun countOutsideQuotes(s: String, c: Char): Int {
        var q = false
        var n = 0
        for (ch in s) {
            if (ch == '"') q = !q else if (!q && ch == c) n++
        }
        return n
    }

    fun parse(text: String, d: Char): List<List<String>> {
        val s = text.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val f = StringBuilder()
        var q = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (q) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        f.append('"'); i++
                    } else q = false
                } else f.append(ch)
            } else {
                when (ch) {
                    '"' -> if (f.isEmpty()) q = true else f.append(ch)
                    d -> { row.add(f.toString()); f.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row.add(f.toString()); f.setLength(0)
                        rows.add(row); row = ArrayList()
                    }
                    else -> f.append(ch)
                }
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) {
            row.add(f.toString()); rows.add(row)
        }
        return rows.filterNot { it.size == 1 && it[0].isEmpty() }
    }

    fun parseTable(text: String, d: Char = detectDelimiter(text)) = CsvTable.from(parse(text, d))
}

object CsvWriter {
    fun write(rows: List<List<String>>, d: Char = ','): String {
        val sb = StringBuilder()
        rows.forEachIndexed { idx, r ->
            if (idx > 0) sb.append("\r\n")
            r.forEachIndexed { i, cell ->
                if (i > 0) sb.append(d)
                sb.append(escape(cell, d))
            }
        }
        return sb.toString()
    }

    fun write(t: CsvTable, d: Char = ','): String = write(listOf(t.header) + t.rows, d)

    private fun escape(s: String, d: Char): String =
        if (s.indexOf(d) >= 0 || s.contains('"') || s.contains('\n') || s.contains('\r'))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s
}

/** Column statistics for the Analysis screen and the column sheet. */
data class ColumnStats(
    val name: String,
    val filled: Int,
    val empty: Int,
    val unique: Int,
    val numeric: Boolean,
    val min: Double?,
    val max: Double?,
    val mean: Double?,
    val sum: Double?,
    val top: List<Pair<String, Int>>,
)

object Analysis {
    fun toNumber(s: String): Double? {
        val t = s.trim().replace(",", "").removePrefix("$").removeSuffix("%")
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()
    }

    fun column(t: CsvTable, c: Int): ColumnStats {
        val values = t.rows.map { it.getOrElse(c) { "" } }
        val filledValues = values.filter { it.isNotBlank() }
        val nums = filledValues.mapNotNull(::toNumber)
        val numeric = filledValues.isNotEmpty() && nums.size >= filledValues.size * 0.8
        val counts = filledValues.groupingBy { it }.eachCount()
        return ColumnStats(
            name = t.header[c],
            filled = filledValues.size,
            empty = values.size - filledValues.size,
            unique = counts.size,
            numeric = numeric,
            min = if (numeric) nums.minOrNull() else null,
            max = if (numeric) nums.maxOrNull() else null,
            mean = if (numeric && nums.isNotEmpty()) nums.average() else null,
            sum = if (numeric) nums.sum() else null,
            top = counts.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
        )
    }

    fun duplicateRows(t: CsvTable): Int = t.rows.size - t.rows.toSet().size

    fun emptyCells(t: CsvTable): Int = t.rows.sumOf { r -> r.count { it.isBlank() } }
}
