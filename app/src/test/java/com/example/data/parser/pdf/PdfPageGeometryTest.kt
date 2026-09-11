package com.example.data.parser.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageGeometryTest {

    /** A4 in points. */
    private val a4 = PdfPageSize(595f, 842f)

    @Test
    fun `a tall page is letterboxed left and right`() {
        // 1000x1000 container, A4 page: height is the limiting dimension.
        val fit = PdfPageGeometry.fit(a4, 1000f, 1000f)!!

        assertEquals(1000f / 842f, fit.scale, 0.0001f)
        assertEquals(842f * fit.scale, fit.height, 0.01f)
        assertEquals(0f, fit.offsetY, 0.01f)
        assertEquals((1000f - 595f * fit.scale) / 2f, fit.offsetX, 0.01f)
    }

    @Test
    fun `a wide container letterboxes top and bottom`() {
        val fit = PdfPageGeometry.fit(PdfPageSize(1000f, 100f), 500f, 500f)!!

        assertEquals(0.5f, fit.scale, 0.0001f)
        assertEquals(0f, fit.offsetX, 0.01f)
        assertEquals(225f, fit.offsetY, 0.01f)
    }

    @Test
    fun `degenerate sizes produce no fit box`() {
        assertNull(PdfPageGeometry.fit(PdfPageSize(0f, 842f), 100f, 100f))
        assertNull(PdfPageGeometry.fit(a4, 0f, 100f))
    }

    @Test
    fun `view and pdf coordinates round trip`() {
        val fit = PdfPageGeometry.fit(a4, 1000f, 1000f)!!
        val rect = PdfRect(100f, 200f, 180f, 215f)

        val view = PdfPageGeometry.pdfToView(rect, fit)
        val backTopLeft = PdfPageGeometry.viewToPdf(view.left, view.top, fit)

        assertEquals(rect.left, backTopLeft.x, 0.01f)
        assertEquals(rect.top, backTopLeft.y, 0.01f)
        assertEquals(rect.width * fit.scale, view.width, 0.01f)
    }

    @Test
    fun `the centre of the container maps to the centre of the page`() {
        val fit = PdfPageGeometry.fit(a4, 1000f, 1000f)!!
        val centre = PdfPageGeometry.viewToPdf(500f, 500f, fit)

        assertEquals(595f / 2f, centre.x, 0.01f)
        assertEquals(842f / 2f, centre.y, 0.01f)
    }

    @Test
    fun `touches on the letterboxing are rejected`() {
        val fit = PdfPageGeometry.fit(a4, 1000f, 1000f)!!

        assertTrue(PdfPageGeometry.isInsidePage(500f, 500f, fit))
        assertFalse(PdfPageGeometry.isInsidePage(2f, 500f, fit))
    }

    @Test
    fun `pan is clamped to the overflow of the zoomed page`() {
        // At 2x on a 1000pt wide container the page overflows by 500pt, 250 on each side.
        val clamped = PdfPageGeometry.clampPan(9999f, -9999f, scale = 2f, 1000f, 800f)

        assertEquals(500f, clamped.x, 0.01f)
        assertEquals(-400f, clamped.y, 0.01f)
    }

    @Test
    fun `an unzoomed page cannot be panned at all`() {
        val clamped = PdfPageGeometry.clampPan(120f, 90f, scale = 1f, 1000f, 800f)

        assertEquals(0f, clamped.x, 0.0f)
        assertEquals(0f, clamped.y, 0.0f)
    }
}
