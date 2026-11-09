package com.ayaz.viewer.xlsx

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Yeni, boş XLSX çalışma kitabı oluşturur (Excel, LibreOffice ve Google E-Tablolar ile uyumlu en küçük paket). */
object XlsxTemplates {

    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

    fun blankWorkbook(target: File, sheetName: String = "Sayfa1") {
        val safeName = sheetName.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifEmpty { "Sayfa1" }
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="$REL/extended-properties" Target="docProps/app.xml"/></Relationships>""",
            "docProps/app.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Ayaz Viewer</Application></Properties>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="$MAIN" xmlns:r="$REL"><bookViews><workbookView/></bookViews><sheets><sheet name="${XmlEsc.attr(safeName)}" sheetId="1" r:id="rId1"/></sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="$MAIN" xmlns:r="$REL"><sheetViews><sheetView workbookViewId="0"/></sheetViews><sheetFormatPr defaultRowHeight="15"/><sheetData/><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/></worksheet>""",
        )
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { z ->
            for ((name, body) in parts) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
    }
}
