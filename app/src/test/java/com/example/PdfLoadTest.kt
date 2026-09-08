package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.parser.PdfBookParser
import com.example.data.parser.PdfTextExtractor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfLoadTest {

    @Test
    fun testParseSmallPdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File("/tmp/test_download.pdf")
        assertTrue("Test file exists", file.exists())

        println("Testing PdfTextExtractor on small PDF...")
        try {
            val pages = PdfTextExtractor.extractPages(context, Uri.fromFile(file), 1)
            println("Extracted ${pages.size} pages from small PDF. Page 1 text: ${pages.firstOrNull()?.text?.take(100)}")
        } catch (t: Throwable) {
            println("ExtractPages failed: ${t.javaClass.name}: ${t.message}")
            t.printStackTrace()
        }
    }

    @Test
    fun testParseTracemonkeyPdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File("/tmp/tracemonkey.pdf")
        assertTrue("Test file exists", file.exists())

        println("Testing PdfTextExtractor on tracemonkey PDF (1MB)...")
        try {
            val pages = PdfTextExtractor.extractPages(context, Uri.fromFile(file), 14)
            println("Extracted ${pages.size} pages from tracemonkey. Page 1 text: ${pages.firstOrNull()?.text?.take(100)}")
        } catch (t: Throwable) {
            println("ExtractPages failed on tracemonkey: ${t.javaClass.name}: ${t.message}")
            t.printStackTrace()
        }
    }

    @Test
    fun testPdfDocumentRenderer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File("/tmp/test_download.pdf")
        val parsed = PdfBookParser.parse(context, Uri.fromFile(file), "Downloaded Test PDF")
        println("Parsed title: ${parsed.title}, chapters: ${parsed.chapters.size}, totalPages: ${parsed.totalPagesEstimate}")
        assertTrue("Parsed should have at least 1 chapter", parsed.chapters.isNotEmpty())

        // Ensure createRenderer does not crash even if native libpdfium is absent on JVM Robolectric
        val renderer = PdfBookParser.createRenderer(context, Uri.fromFile(file))
        println("Renderer created: $renderer, pageCount: ${renderer?.pageCount}")
        renderer?.close()
    }
}
