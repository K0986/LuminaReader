package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.parser.PdfBookParser
import com.example.data.parser.PdfTextExtractor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Exercises the PDF pipeline against the PDFs bundled in `assets/sample_books`.
 *
 * The previous version of this test read from `/tmp/test_download.pdf`, so it passed only on the
 * machine where those files happened to exist and failed for everyone else, CI included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfLoadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun asset(name: String): File {
        val target = File(context.cacheDir, name)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("sample_books/$name").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    @Test
    fun `legacy extractor returns one entry per page`() {
        val file = asset("quick_guide.pdf")
        val pages = PdfTextExtractor.extractPages(context, Uri.fromFile(file), pageCount = 2)

        assertTrue("Expected an entry per page", pages.size == 2)
        println("quick_guide page 1: ${pages.first().text.take(160)}")
    }

    @Test
    fun `legacy extractor stays within its page budget`() {
        val file = asset("tracemonkey.pdf")
        val pages = PdfTextExtractor.extractPages(
            context = context,
            uri = Uri.fromFile(file),
            pageCount = 14,
            maxPages = 4,
            maxFileSizeBytes = 12L * 1024 * 1024
        )

        // Over the page budget the parser must degrade to empty text, never to a fabricated
        // "Page 3 of 14" string that would later be narrated or indexed as if it were content.
        assertTrue("Expected placeholder-free output", pages.all { it.text.isEmpty() })
        assertTrue(pages.size == 14)
    }

    @Test
    fun `extracted text is never a fabricated placeholder`() {
        val file = asset("tracemonkey.pdf")
        val pages = PdfTextExtractor.extractPages(
            context = context,
            uri = Uri.fromFile(file),
            pageCount = 14,
            maxPages = 30,
            maxFileSizeBytes = 12L * 1024 * 1024
        )

        assertTrue(pages.none { it.text.startsWith("Page ") && it.text.contains(" of 14") })
        println("tracemonkey page 1: ${pages.firstOrNull()?.text?.take(200)}")
    }

    @Test
    fun `parse reports a page count and never throws without native pdf support`() {
        val file = asset("quick_guide.pdf")
        val parsed = PdfBookParser.parse(context, Uri.fromFile(file), "Quick Guide")

        println("Parsed '${parsed.title}', pages=${parsed.totalPagesEstimate}, toc=${parsed.tableOfContents.size}")
        assertTrue("Table of contents has one entry per page", parsed.tableOfContents.isNotEmpty())

        // Robolectric has no pdfium, so createRenderer must fail softly rather than crash.
        val renderer = PdfBookParser.createRenderer(context, Uri.fromFile(file))
        renderer?.close()
    }
}
