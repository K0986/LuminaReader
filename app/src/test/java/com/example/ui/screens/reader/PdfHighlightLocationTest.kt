package com.example.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfHighlightLocationTest {

    @Test
    fun `locations round trip`() {
        val decoded = PdfHighlightLocation.decode(PdfHighlightLocation.encode(17, 240, 268))!!

        assertEquals(17, decoded.pageIndex)
        assertEquals(240, decoded.startChar)
        assertEquals(268, decoded.endChar)
    }

    @Test
    fun `reversed ranges are normalised`() {
        val decoded = PdfHighlightLocation.decode("pdf:3:90-12")!!

        assertEquals(12, decoded.startChar)
        assertEquals(90, decoded.endChar)
    }

    @Test
    fun `locations from reflowable books are ignored`() {
        assertNull(PdfHighlightLocation.decode("p:4:para:2:0-31"))
        assertNull(PdfHighlightLocation.decode("page:4"))
        assertNull(PdfHighlightLocation.decode("pdf:x:1-2"))
        assertNull(PdfHighlightLocation.decode("pdf:1:oops"))
    }
}
