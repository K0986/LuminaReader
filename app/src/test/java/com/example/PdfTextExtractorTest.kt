package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.parser.PdfPageText
import com.example.data.parser.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

/**
 * Exercises PDF text extraction against both synthesised documents and the real PDFs
 * already bundled in `app/src/main/assets/sample_books`.
 *
 * The previous `PdfLoadTest` read `/tmp/test_download.pdf` and `/tmp/tracemonkey.pdf` --
 * absolute paths outside the repository, so the suite failed on any machine but the one
 * it was written on. Worse, every body was wrapped in
 * `try { ... } catch (t: Throwable) { println(t) }` and asserted nothing whatsoever about
 * the extracted text, so extraction returning *nothing at all* still counted as a pass.
 * That is exactly how the tracemonkey regression went unnoticed.
 *
 * Synthetic fixtures are built in-process rather than checked in as binaries, which keeps
 * each regression pinned to the specific structure that caused it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextExtractorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Copies a bundled asset out to a real file, since PdfRenderer needs a descriptor. */
    private fun asset(name: String): File {
        val target = File.createTempFile(name.substringAfterLast('/'), ".pdf")
        context.assets.open(name).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    // ------------------------------------------------------------- real bundled samples

    @Test
    fun `extracts prose from the bundled single-page sample`() {
        val page = PdfTextExtractor.extractPage(
            context, Uri.fromFile(asset("sample_books/quick_guide.pdf")), 0
        )

        assertTrue("Expected a text layer, got source=${page.source}", page.hasText)
        val text = checkNotNull(page.text)
        assertTrue("Expected the document heading, got: ${text.take(140)}", text.contains("Sample PDF"))
        assertTrue("Expected body prose", text.contains("Lorem ipsum dolor sit amet"))
    }

    @Test
    fun `extracts prose from the bundled multi-page academic paper`() {
        // Regression guard. This 14-page, 1 MB paper previously yielded zero characters:
        // the old extractor bailed out above 30 pages or 3 MB, and even within those
        // limits misread indirect `/Length` references, so the reader displayed
        // "Page 1 of 14" as the page's own text.
        val uri = Uri.fromFile(asset("sample_books/tracemonkey.pdf"))

        val first = PdfTextExtractor.extractPage(context, uri, 0)
        assertTrue("Page 1 should have a text layer", first.hasText)
        assertTrue(
            "Expected the paper's subject matter, got: ${first.text?.take(160)}",
            first.text!!.contains("Trace", ignoreCase = true)
        )

        val later = PdfTextExtractor.extractPage(context, uri, 5)
        assertTrue("Page 6 should also have a text layer", later.hasText)
        assertFalse("Pages must not be confused with one another", later.text == first.text)
    }

    @Test
    fun `never emits page-number placeholders as prose`() {
        // The old fallback substituted the literal string "Page 1 of 14".
        val page = PdfTextExtractor.extractPage(
            context, Uri.fromFile(asset("sample_books/tracemonkey.pdf")), 0
        )
        assertFalse(
            "Extractor must not fabricate placeholder text",
            page.text.orEmpty().trim().matches(Regex("""Page \d+ of \d+"""))
        )
    }

    // ------------------------------------------------------------- synthetic structures

    @Test
    fun `reads a content stream whose Length is a direct integer`() {
        val pdf = writePdf(listOf("Hello direct length"), compress = false, indirectLength = false)
        val page = PdfTextExtractor.extractPage(context, Uri.fromFile(pdf), 0)

        assertTrue(page.hasText)
        assertTrue(page.text!!.contains("Hello direct length"))
    }

    @Test
    fun `reads a Flate stream whose Length is an indirect reference`() {
        // `/Length 5 0 R` points at another object holding the byte count. Reading the
        // first integer after `/Length` -- as the old code did -- yields a five-byte
        // payload, and inflating five bytes of a deflate stream fails, silently losing
        // the whole page. This is what broke the bundled single-page sample.
        val pdf = writePdf(listOf("Indirect length survives"), compress = true, indirectLength = true)
        val page = PdfTextExtractor.extractPage(context, Uri.fromFile(pdf), 0)

        assertTrue("Indirect /Length must be resolved", page.hasText)
        assertTrue(page.text!!.contains("Indirect length survives"))
    }

    @Test
    fun `maps each page to its own content stream`() {
        val pdf = writePdf(
            listOf("First page body", "Second page body", "Third page body"),
            compress = true,
            indirectLength = true
        )
        val uri = Uri.fromFile(pdf)

        assertTrue(PdfTextExtractor.extractPage(context, uri, 0).text!!.contains("First page body"))
        assertTrue(PdfTextExtractor.extractPage(context, uri, 1).text!!.contains("Second page body"))
        assertTrue(PdfTextExtractor.extractPage(context, uri, 2).text!!.contains("Third page body"))
    }

    @Test
    fun `page counts above the old thirty page cut-off are still extracted`() {
        // The previous implementation returned placeholders for any document with more
        // than 30 pages, which is most real books.
        val pages = (1..40).map { "Body of page $it" }
        val pdf = writePdf(pages, compress = true, indirectLength = true)

        val page35 = PdfTextExtractor.extractPage(context, Uri.fromFile(pdf), 34)
        assertTrue("Page 35 of 40 must still be readable", page35.hasText)
        assertTrue(page35.text!!.contains("Body of page 35"))
    }

    // -------------------------------------------------------------------- failure modes

    @Test
    fun `reports NONE rather than throwing for a non-pdf`() {
        val notAPdf = File.createTempFile("garbage", ".pdf").apply { writeText("this is not a pdf") }
        val page = PdfTextExtractor.extractPage(context, Uri.fromFile(notAPdf), 0)

        assertNotNull(page)
        assertEquals(PdfPageText.Source.NONE, page.source)
        assertFalse(page.hasText)
    }

    @Test
    fun `out of range page indices report no text`() {
        val pdf = writePdf(listOf("only page"), compress = false, indirectLength = false)
        assertFalse(PdfTextExtractor.extractPage(context, Uri.fromFile(pdf), 99).hasText)
    }

    // ------------------------------------------------------------------- pure unit tests

    @Test
    fun `paragraph splitting collapses blank runs and normalises inner whitespace`() {
        assertEquals(
            listOf("First para", "Second para"),
            PdfTextExtractor.splitParagraphs("First  para\n\n\n  Second\tpara  ")
        )
    }

    @Test
    fun `decodes escape sequences in literal pdf strings`() {
        assertEquals("a(b)c", PdfTextExtractor.decodePdfString("""(a\(b\)c)"""))
        assertEquals("line\nbreak", PdfTextExtractor.decodePdfString("""(line\nbreak)"""))
        assertEquals("A", PdfTextExtractor.decodePdfString("""(\101)""")) // octal escape
    }

    @Test
    fun `reads text showing operators in document order`() {
        val stream = """
            BT
            [(Hello) -200 (world)] TJ
            T*
            (second line) Tj
            ET
        """.trimIndent()

        val lines = PdfTextExtractor.extractTextOperators(stream).lines().filter { it.isNotBlank() }
        assertEquals(listOf("Hello world", "second line"), lines)
    }

    @Test
    fun `treats a large negative kern as a word gap`() {
        val text = PdfTextExtractor.extractTextOperators("BT [(alpha) -400 (beta)] TJ ET")
        assertEquals("alpha beta", text.trim())
    }

    // ----------------------------------------------------------------- fixture generator

    /**
     * Writes a minimal but structurally valid PDF, one content stream per entry in
     * [bodies]. [compress] applies `/FlateDecode`; [indirectLength] writes the stream
     * length as `/Length N 0 R` instead of a literal integer.
     */
    private fun writePdf(bodies: List<String>, compress: Boolean, indirectLength: Boolean): File {
        val out = ByteArrayOutputStream()
        fun emit(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

        emit("%PDF-1.4\n")

        val pageIds = bodies.indices.map { 10 + it * 3 }
        val catalogId = 1
        val pagesId = 2

        emit("$catalogId 0 obj\n<< /Type /Catalog /Pages $pagesId 0 R >>\nendobj\n")
        emit(
            "$pagesId 0 obj\n<< /Type /Pages /Count ${bodies.size} /Kids [" +
                pageIds.joinToString(" ") { "$it 0 R" } + "] >>\nendobj\n"
        )

        bodies.forEachIndexed { index, body ->
            val pageId = pageIds[index]
            val contentId = pageId + 1
            val lengthId = pageId + 2

            emit(
                "$pageId 0 obj\n<< /Type /Page /Parent $pagesId 0 R " +
                    "/Contents $contentId 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
            )

            val stream = "BT (${body.replace("(", "\\(").replace(")", "\\)")}) Tj ET\n"
            val payload = if (compress) deflate(stream) else stream.toByteArray(Charsets.ISO_8859_1)
            val lengthField = if (indirectLength) "/Length $lengthId 0 R" else "/Length ${payload.size}"
            val filterField = if (compress) " /Filter /FlateDecode" else ""

            emit("$contentId 0 obj\n<< $lengthField$filterField >>\nstream\n")
            out.write(payload)
            emit("\nendstream\nendobj\n")

            if (indirectLength) emit("$lengthId 0 obj\n${payload.size}\nendobj\n")
        }

        emit("trailer\n<< /Root $catalogId 0 R /Size ${pageIds.size * 3 + 3} >>\n%%EOF\n")

        return File.createTempFile("synthetic", ".pdf").apply { writeBytes(out.toByteArray()) }
    }

    private fun deflate(text: String): ByteArray {
        val input = text.toByteArray(Charsets.ISO_8859_1)
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(input.size * 2 + 64)
        val produced = deflater.deflate(buffer)
        deflater.end()
        return buffer.copyOf(produced)
    }
}
