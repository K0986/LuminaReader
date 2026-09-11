package com.example.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IncomingBookIntentTest {

    private val pdf: Uri = Uri.parse("content://com.android.providers.downloads/document/42")
    private val epub: Uri = Uri.parse("content://com.google.android.apps.docs/document/abc")

    @Test
    fun `open with passes the document as intent data`() {
        val intent = Intent(Intent.ACTION_VIEW, pdf)

        assertEquals(listOf(pdf), IncomingBookIntent.bookUris(intent))
    }

    @Test
    fun `share sheet passes a single document in EXTRA_STREAM`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdf)
        }

        assertEquals(listOf(pdf), IncomingBookIntent.bookUris(intent))
    }

    @Test
    fun `sharing several books returns them in order`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, arrayListOf(pdf, epub))
        }

        assertEquals(listOf(pdf, epub), IncomingBookIntent.bookUris(intent))
    }

    @Test
    fun `senders that only populate ClipData are still understood`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/epub+zip"
            clipData = ClipData(
                ClipDescription("book", arrayOf("application/epub+zip")),
                ClipData.Item(epub)
            )
        }

        assertEquals(listOf(epub), IncomingBookIntent.bookUris(intent))
    }

    @Test
    fun `the same file arriving twice is de-duplicated`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdf)
            clipData = ClipData(ClipDescription("book", arrayOf("application/pdf")), ClipData.Item(pdf))
        }

        assertEquals(listOf(pdf), IncomingBookIntent.bookUris(intent))
    }

    @Test
    fun `shared plain text without a file is ignored`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Look at this quote")
        }

        assertTrue(IncomingBookIntent.bookUris(intent).isEmpty())
        assertTrue(IncomingBookIntent.bookUris(null).isEmpty())
    }

    @Test
    fun `web links are not treated as local files`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/book.pdf"))

        assertTrue(IncomingBookIntent.bookUris(intent).isEmpty())
    }

    @Test
    fun `extension matching covers the formats the app can parse`() {
        assertTrue(IncomingBookIntent.hasSupportedExtension("Deep Work.EPUB"))
        assertTrue(IncomingBookIntent.hasSupportedExtension("notes.txt"))
        assertFalse(IncomingBookIntent.hasSupportedExtension("holiday.jpg"))
        assertFalse(IncomingBookIntent.hasSupportedExtension(null))
    }
}
