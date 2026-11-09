package com.ayaz.viewer.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellRefTest {

    @Test
    fun columnNames() {
        assertEquals("A", CellRef.columnName(0))
        assertEquals("Z", CellRef.columnName(25))
        assertEquals("AA", CellRef.columnName(26))
        assertEquals("AZ", CellRef.columnName(51))
        assertEquals("ZZ", CellRef.columnName(701))
        assertEquals("AAA", CellRef.columnName(702))
        assertEquals("XFD", CellRef.columnName(16383))
    }

    @Test
    fun columnIndexRoundTrip() {
        for (i in 0 until 2000) assertEquals(i, CellRef.columnIndex(CellRef.columnName(i)))
    }

    @Test
    fun parseReferences() {
        val p = CellRef.parse("AB12")
        assertEquals(11, CellRef.rowOf(p))
        assertEquals(27, CellRef.colOf(p))
        val abs = CellRef.parse("\$C\$3")
        assertEquals(2, CellRef.rowOf(abs))
        assertEquals(2, CellRef.colOf(abs))
        assertTrue(CellRef.parse("1A") < 0)
        assertTrue(CellRef.parse("") < 0)
        assertTrue(CellRef.parse("A0") < 0)
        assertEquals("B7", CellRef.name(6, 1))
    }

    @Test
    fun parseRanges() {
        assertEquals(CellRange(0, 0, 2, 1), CellRange.parse("B3:A1"))
        assertEquals(CellRange(4, 3, 4, 3), CellRange.parse("D5"))
        assertNull(CellRange.parse("A:A"))
        assertNull(CellRange.parse("garbage!"))
    }
}
