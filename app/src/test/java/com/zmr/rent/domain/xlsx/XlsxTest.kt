package com.zmr.rent.domain.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XlsxTest {

    @Test
    fun `column letters`() {
        assertEquals("A", columnName(0))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("AZ", columnName(51))
        assertEquals(0, columnIndex("A"))
        assertEquals(26, columnIndex("AA"))
        assertEquals(51, columnIndex("AZ"))
    }

    @Test
    fun `what is written can be read back`() {
        val bytes = XlsxWriter.toBytes(
            listOf(
                Sheet(
                    name = "Payments",
                    rows = listOf(
                        listOf(Cell.Text("Renter", CellStyle.BOLD), Cell.Text("Amount", CellStyle.BOLD)),
                        listOf(Cell.Text("रमेश <Kumar> & sons"), Cell.Money(140_050)),
                        listOf(Cell.Text("Count"), Cell.Empty, Cell.Count(3)),
                    ),
                    frozenRows = 1,
                ),
                Sheet(name = "About: info?", rows = listOf(listOf(Cell.Text("format"), Cell.Text("zmr-report")))),
            ),
        )

        val sheets = XlsxReader.read(bytes.inputStream())

        assertEquals(listOf("Payments", "About info"), sheets.keys.toList())
        assertEquals(
            listOf(
                listOf("Renter", "Amount"),
                listOf("रमेश <Kumar> & sons", "1400.50"),
                listOf("Count", "", "3"),
            ),
            sheets["Payments"],
        )
    }

    @Test
    fun `reads shared strings, as Excel saves them`() {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                fun entry(path: String, xml: String) {
                    zip.putNextEntry(java.util.zip.ZipEntry(path))
                    zip.write(xml.toByteArray())
                    zip.closeEntry()
                }
                val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                entry(
                    "xl/workbook.xml",
                    """<workbook xmlns="$ns" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
                )
                entry(
                    "xl/_rels/workbook.xml.rels",
                    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet1.xml"/></Relationships>""",
                )
                entry(
                    "xl/sharedStrings.xml",
                    """<sst xmlns="$ns"><si><t>Name</t></si><si><r><t>Ra</t></r><r><t>mesh</t></r></si></sst>""",
                )
                entry(
                    "xl/worksheets/sheet1.xml",
                    """<worksheet xmlns="$ns"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row><row r="3"><c r="B3" t="s"><v>1</v></c><c r="C3"><v>12.5</v></c></row></sheetData></worksheet>""",
                )
            }
        }.toByteArray()

        assertEquals(
            listOf(listOf("Name"), emptyList(), listOf("", "Ramesh", "12.5")),
            XlsxReader.read(bytes.inputStream())["Data"],
        )
    }

    @Test
    fun `refuses a file that is not a spreadsheet`() {
        assertThrows(XlsxReader.NotASpreadsheetException::class.java) {
            XlsxReader.read("hello".byteInputStream())
        }
    }
}
