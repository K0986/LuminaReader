package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * End-to-end check of the text index: extract, persist, resume, read back and search.
 *
 * Robolectric has no pdfium, so [com.example.data.parser.pdf.PdfTextEngines] falls back to the
 * in-app content-stream parser here — which is exactly the path that runs on devices too old for
 * the platform text layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextIndexerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sampleBook(): Book {
        val file = File(context.cacheDir, "quick_guide.pdf")
        if (!file.exists() || file.length() == 0L) {
            context.assets.open("sample_books/quick_guide.pdf").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return Book(
            filePath = Uri.fromFile(file).toString(),
            title = "Quick Guide",
            author = "Lumina",
            format = "PDF",
            fileHash = "quick-guide-hash",
            totalPages = 1
        )
    }

    @Test
    fun `indexing persists a row per page and makes the text searchable`() = runTest {
        val db = AppDatabase.getDatabase(context)
        val bookId = db.bookDao().insertBook(sampleBook())
        val book = db.bookDao().getBookByIdDirect(bookId)!!
        val indexer = PdfTextIndexer(context)

        val progress = indexer.index(book, totalPagesHint = 1).toList()

        assertTrue("Progress should finish", progress.last() is PdfIndexProgress.Complete)
        assertEquals(1, indexer.indexedPageCount(bookId))
        assertTrue(indexer.isIndexed(bookId, totalPages = 1))

        val pageText = indexer.pageText(bookId, 0).orEmpty()
        assertTrue("Expected real page text, got '$pageText'", pageText.contains("Sample", ignoreCase = true))

        val hits = indexer.search(bookId, "sample")
        assertEquals(1, hits.size)
        assertEquals(0, hits.first().pageIndex)
        assertTrue(hits.first().snippet.isNotBlank())
    }

    @Test
    fun `re-running the pass is idempotent and skips finished pages`() = runTest {
        val db = AppDatabase.getDatabase(context)
        val bookId = db.bookDao().insertBook(sampleBook().copy(fileHash = "second-hash"))
        val book = db.bookDao().getBookByIdDirect(bookId)!!
        val indexer = PdfTextIndexer(context)

        indexer.index(book, totalPagesHint = 1).toList()
        val afterFirst = indexer.pageText(bookId, 0)
        indexer.index(book, totalPagesHint = 1).toList()

        assertEquals(1, indexer.indexedPageCount(bookId))
        assertEquals(afterFirst, indexer.pageText(bookId, 0))
    }

    @Test
    fun `deleting a book takes its index with it`() = runTest {
        val db = AppDatabase.getDatabase(context)
        val bookId = db.bookDao().insertBook(sampleBook().copy(fileHash = "third-hash"))
        val book = db.bookDao().getBookByIdDirect(bookId)!!
        val indexer = PdfTextIndexer(context)
        indexer.index(book, totalPagesHint = 1).toList()
        assertEquals(1, indexer.indexedPageCount(bookId))

        db.bookDao().deleteBook(book)

        assertEquals(0, indexer.indexedPageCount(bookId))
        assertTrue(db.bookDao().getAllBooks().first().none { it.id == bookId })
    }
}
