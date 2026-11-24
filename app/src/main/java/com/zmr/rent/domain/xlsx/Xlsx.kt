package com.zmr.rent.domain.xlsx

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** How a cell looks. */
enum class CellStyle { NORMAL, BOLD, MONEY, BOLD_MONEY, TITLE, HEADER, DELAY }

/** One spreadsheet cell. Money is written as a real number so it can be added up in Excel. */
sealed interface Cell {
    data class Text(val value: String, val style: CellStyle = CellStyle.NORMAL) : Cell
    data class Money(val paise: Long, val style: CellStyle = CellStyle.MONEY) : Cell
    data class Count(val value: Long, val style: CellStyle = CellStyle.NORMAL) : Cell
    data object Empty : Cell
}

data class Sheet(
    val name: String,
    val rows: List<List<Cell>>,
    /** Column widths in characters; missing columns use Excel's default. */
    val columnWidths: List<Int> = emptyList(),
    /** Rows kept visible at the top while scrolling (the header). */
    val frozenRows: Int = 0,
)

/**
 * Writes .xlsx files (Excel 2007 and newer, also opened by Google Sheets, WPS Office and
 * LibreOffice). Only what reports need: text, numbers, bold, a money format, column widths and
 * a frozen header row. Written by hand to keep the app small; a full spreadsheet library would
 * add several megabytes.
 */
object XlsxWriter {

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty())
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet -> entry("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet)) }
        }
    }

    fun toBytes(sheets: List<Sheet>): ByteArray = ByteArrayOutputStream().also { write(sheets, it) }.toByteArray()

    private fun contentTypes(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escape(sheetName(sheet.name))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    // Style indexes used in cells: 0 normal, 1 bold, 2 money, 3 bold money, 4 title, 5 ZMR blue header, 6 ZMR red delay.
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF6D9EEB"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEA9999"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun styleIndex(style: CellStyle) = when (style) {
        CellStyle.NORMAL -> 0
        CellStyle.BOLD -> 1
        CellStyle.MONEY -> 2
        CellStyle.BOLD_MONEY -> 3
        CellStyle.TITLE -> 4
        CellStyle.HEADER -> 5
        CellStyle.DELAY -> 6
    }

    private fun sheetXml(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (sheet.frozenRows > 0) {
            val top = "A${sheet.frozenRows + 1}"
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${sheet.frozenRows}" topLeftCell="$top" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        }
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { index, width ->
                append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { columnIndex, cell ->
                val ref = columnName(columnIndex) + rowNumber
                when (cell) {
                    is Cell.Text -> append(
                        """<c r="$ref" t="inlineStr" s="${styleIndex(cell.style)}"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""",
                    )
                    is Cell.Money -> append("""<c r="$ref" s="${styleIndex(cell.style)}"><v>${rupees(cell.paise)}</v></c>""")
                    is Cell.Count -> append("""<c r="$ref" s="${styleIndex(cell.style)}"><v>${cell.value}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** "1400.50" from 140050 paise; exact, with no floating point. */
    private fun rupees(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = Math.abs(paise)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** Excel sheet names: at most 31 characters, none of : \ / ? * [ ]. */
    private fun sheetName(name: String) = name.filterNot { it in ":\\/?*[]" }.take(31).ifEmpty { "Sheet" }

    /** Escapes XML and drops control characters that XML does not allow. */
    private fun escape(text: String) = buildString {
        for (char in text) {
            when {
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char < ' ' && char != '\t' && char != '\n' && char != '\r' -> Unit
                else -> append(char)
            }
        }
    }
}

/** "A" for column 0, "Z" for 25, "AA" for 26. */
internal fun columnName(index: Int): String {
    var n = index + 1
    val name = StringBuilder()
    while (n > 0) {
        val remainder = (n - 1) % 26
        name.insert(0, 'A' + remainder)
        n = (n - 1) / 26
    }
    return name.toString()
}

/** 0 for "A", 26 for "AA". */
internal fun columnIndex(letters: String): Int = letters.fold(0) { total, char -> total * 26 + (char - 'A' + 1) } - 1

/**
 * Reads the cell values of an .xlsx file as text, sheet by sheet. Handles files written by this
 * app and the same files after being opened and saved again in Excel or Google Sheets.
 */
object XlsxReader {

    class NotASpreadsheetException(message: String) : Exception(message)

    private const val MAX_TOTAL_BYTES = 30L * 1024 * 1024
    private const val MAX_ENTRIES = 500

    /** Sheet name to rows; each row is a list of cell texts, "" for empty cells. */
    fun read(input: InputStream): Map<String, List<List<String>>> {
        val files = unzip(input)
        val workbook = files["xl/workbook.xml"] ?: throw NotASpreadsheetException("No workbook")
        val relations = files["xl/_rels/workbook.xml.rels"]?.let(::parse)
            ?.elements("Relationship")
            ?.associate { it.getAttribute("Id") to it.getAttribute("Target") }
            .orEmpty()
        val sharedStrings = files["xl/sharedStrings.xml"]?.let(::parse)?.elements("si")?.map { it.allText() }.orEmpty()

        return parse(workbook).elements("sheet").associate { sheet ->
            val relationId = sheet.getAttributeNS(RELATIONSHIPS_NS, "id").ifEmpty { sheet.getAttribute("r:id") }
            val target = relations[relationId].orEmpty().removePrefix("/").removePrefix("xl/")
            val xml = files["xl/$target"] ?: throw NotASpreadsheetException("Missing sheet ${sheet.getAttribute("name")}")
            sheet.getAttribute("name") to readSheet(parse(xml), sharedStrings)
        }
    }

    private fun readSheet(document: Element, sharedStrings: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var nextRow = 0
        for (row in document.elements("row")) {
            val rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: nextRow
            nextRow = rowIndex + 1
            val cells = rows.getOrPut(rowIndex) { sortedMapOf() }
            var nextColumn = 0
            for (cell in row.elements("c")) {
                val ref = cell.getAttribute("r")
                val column = if (ref.isNotEmpty()) columnIndex(ref.takeWhile { it.isLetter() }) else nextColumn
                nextColumn = column + 1
                cells[column] = when (cell.getAttribute("t")) {
                    "s" -> sharedStrings.getOrElse(cell.firstText("v").toIntOrNull() ?: -1) { "" }
                    "inlineStr" -> cell.elements("is").firstOrNull()?.allText().orEmpty()
                    else -> cell.firstText("v")
                }
            }
        }
        val lastRow = rows.keys.lastOrNull() ?: return emptyList()
        return (0..lastRow).map { index ->
            val cells = rows[index].orEmpty()
            val lastColumn = cells.keys.maxOrNull() ?: -1
            (0..lastColumn).map { cells[it].orEmpty() }
        }
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (files.size >= MAX_ENTRIES) throw NotASpreadsheetException("Too many parts")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_TOTAL_BYTES) throw NotASpreadsheetException("File too large")
                        out.write(buffer, 0, count)
                    }
                    files[entry.name.removePrefix("/")] = out.toByteArray()
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw NotASpreadsheetException("Not a zip file")
        }
        if (files.isEmpty()) throw NotASpreadsheetException("Empty file")
        return files
    }

    private fun parse(xml: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Refuse DTDs and external entities: the file came from outside the app.
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        return try {
            factory.newDocumentBuilder().parse(xml.inputStream()).documentElement
        } catch (e: Exception) {
            throw NotASpreadsheetException("Unreadable XML")
        }
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (e: Exception) {
            // Not every XML parser knows every feature; Android's ignores DTDs anyway.
        }
    }

    private fun Element.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.firstText(localName: String) = elements(localName).firstOrNull()?.textContent.orEmpty()

    /** All text inside, joining rich-text runs; skips phonetic hints that Excel stores beside text. */
    private fun Element.allText() = elements("t")
        .filterNot { (it.parentNode as? Element)?.localName == "rPh" }
        .joinToString("") { it.textContent }

    private const val RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}
