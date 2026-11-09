package com.ayaz.viewer.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberFormatTest {

    private val tr = FormatLocale.TURKISH
    private val en = FormatLocale.ENGLISH

    private fun f(code: String, v: Double, loc: FormatLocale = tr, date1904: Boolean = false): String =
        NumberFormat.parse(code).format(v, date1904, loc).text

    @Test
    fun general() {
        assertEquals("0", f("General", 0.0))
        assertEquals("42", f("General", 42.0))
        assertEquals("-0,5", f("General", -0.5))
        assertEquals("0,3", f("General", 0.1 + 0.2))
        assertEquals("0,3333333333", f("General", 1.0 / 3))
        assertEquals("123456789012345", f("General", 123456789012345.0))
        assertEquals("1E+20", f("General", 1e20))
        assertEquals("1,23457E-10", f("General", 1.234567e-10))
        assertEquals("1234.5", f("General", 1234.5, en))
    }

    @Test
    fun fixedAndGrouping() {
        assertEquals("1235", f("0", 1234.5))
        assertEquals("2,68", f("0.00", 2.675))
        assertEquals("1.234.567", f("#,##0", 1234567.0))
        assertEquals("-1.234,50", f("#,##0.00", -1234.5))
        assertEquals("0", f("#,##0", 0.0))
        assertEquals("0,50", f("#,##0.00", 0.5))
        assertEquals("1,234.50", f("#,##0.00", 1234.5, en))
        assertEquals("00042", f("00000", 42.0))
        assertEquals("123-456", f("000-000", 123456.0))
        assertEquals("1,5", f("0.0#", 1.5))
        assertEquals("1,23", f("0.0#", 1.234))
        assertEquals(",5", f("#.##", 0.5))
        assertEquals("1,2", f("0.0,,", 1234567.0))
        assertEquals("1.234,50 ₺", f("#,##0.00 \"₺\"", 1234.5))
        assertEquals("€12", f("[\$€-407]0", 12.0))
    }

    @Test
    fun percentScientificFraction() {
        assertEquals("26%", f("0%", 0.256))
        assertEquals("25,60%", f("0.00%", 0.256))
        assertEquals("-3,3%", f("0.0%", -0.033))
        assertEquals("1,23E+04", f("0.00E+00", 12345.0))
        assertEquals("1,23E-04", f("0.00E+00", 0.000123))
        assertEquals("5,00E+00", f("0.00E+00", 5.0))
        assertEquals("12,3E+3", f("##0.0E+0", 12345.0))
        assertEquals("1 1/2", f("# ?/?", 1.5))
        assertEquals("1/3", f("# ??/??", 0.333))
        assertEquals("3/8", f("# ?/8", 0.375))
        assertEquals("2", f("# ?/?", 2.0))
    }

    @Test
    fun sectionsConditionsColors() {
        assertEquals("(1.234)", f("#,##0_);(#,##0)", -1234.0))
        assertEquals("1.234 ", f("#,##0_);(#,##0)", 1234.0))
        val red = NumberFormat.parse("0.00;[Red]-0.00").format(-5.0, false, tr)
        assertEquals("-5,00", red.text)
        assertEquals(0xFFFF0000.toInt(), red.color)
        assertEquals("sıfır", f("0;-0;\"sıfır\"", 0.0))
        assertEquals("5", f("0;-0;\"sıfır\"", 5.0))
        assertEquals("12K", f("[>=1000]#,##0,\"K\";0", 12345.0))
        assertEquals("500", f("[>=1000]#,##0,\"K\";0", 500.0))
        assertEquals("Toplam: 5", f("\"Toplam: \"General", 5.0))
        assertEquals("", f(";;;", 5.0))
        assertEquals(" - ", f("_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)", 0.0))
    }

    @Test
    fun textSections() {
        assertEquals("Ad: Ayaz", NumberFormat.parse("\"Ad: \"@").formatText("Ayaz").text)
        assertEquals("metin", NumberFormat.parse("0.00").formatText("metin").text)
        assertEquals("[metin]", NumberFormat.parse("0;-0;0;\"[\"@\"]\"").formatText("metin").text)
        // Yalnızca metin bölümü olan biçimde sayılar genel biçimle gösterilir.
        assertEquals("12,5", f("@", 12.5))
    }

    @Test
    fun dates() {
        assertTrue(NumberFormat.parse("dd.mm.yyyy").isDateFormat)
        assertFalse(NumberFormat.parse("#,##0").isDateFormat)
        assertEquals("05.03.2024", f("dd.mm.yyyy", 45356.0))
        assertEquals("05.03.2024", f(BuiltinFormats.code(14)!!, 45356.0))
        assertEquals("5 Mart 2024 Salı", f("d mmmm yyyy dddd", 45356.0))
        assertEquals("5 March 2024 Tuesday", f("d mmmm yyyy dddd", 45356.0, en))
        assertEquals("24-Mar", f("yy-mmm", 45356.0))
        assertEquals("29.02.2000", f("dd.mm.yyyy", 36585.0))
        assertEquals("01.01.1900", f("dd.mm.yyyy", 1.0))
        assertEquals("28.02.1900", f("dd.mm.yyyy", 59.0))
        assertEquals("01.03.1900", f("dd.mm.yyyy", 61.0))
        assertEquals("01.01.1904", f("dd.mm.yyyy", 0.0, date1904 = true))
        assertEquals("M", f("mmmmm", 45356.0))
    }

    @Test
    fun times() {
        assertEquals("6:00 ÖS", f("h:mm AM/PM", 0.75))
        assertEquals("6:00 PM", f("h:mm AM/PM", 0.75, en))
        assertEquals("12:00 ÖS", f("h:mm AM/PM", 0.5))
        assertEquals("12:00 ÖÖ", f("h:mm AM/PM", 0.0))
        assertEquals("12:00:01", f("hh:mm:ss", 0.5 + 1.0 / 86400))
        assertEquals("36:00", f("[h]:mm", 1.5))
        assertEquals("90:30", f("[mm]:ss", 90.5 / 1440))
        assertEquals("00:01,5", f("mm:ss.0", 1.5 / 86400))
        assertEquals("15.01.2024 14:30", f("dd.mm.yyyy hh:mm", 45306.0 + 14.5 / 24))
        // 23:59:59.6 saniyeye yuvarlanınca ertesi güne geçmeli
        assertEquals("02.01.1900 00:00:00", f("dd.mm.yyyy hh:mm:ss", 1.0 + (86399.6 / 86400)))
        assertEquals("########", f("dd.mm.yyyy", -1.0))
    }

    @Test
    fun excelDateRoundTrip() {
        val dt = java.time.LocalDateTime.of(2024, 2, 29, 13, 45, 0)
        val serial = ExcelDate.fromDateTime(dt, false)
        assertEquals(45351.572916, serial, 1e-5)
        assertEquals(dt, ExcelDate.toDateTime(serial, false))
        val s1904 = ExcelDate.fromDateTime(dt, true)
        assertEquals(dt, ExcelDate.toDateTime(s1904, true))
        assertEquals(java.time.LocalDateTime.of(2024, 3, 5, 10, 20, 30), ExcelDate.parseIso("2024-03-05T10:20:30Z"))
        assertEquals(java.time.LocalDateTime.of(2024, 3, 5, 0, 0), ExcelDate.parseIso("2024-03-05"))
    }
}
