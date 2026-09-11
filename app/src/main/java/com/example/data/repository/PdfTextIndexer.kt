package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.Book
import com.example.data.model.PdfPageTextEntity
import com.example.data.parser.pdf.PdfTextCapability
import com.example.data.parser.pdf.PdfTextEngines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** A page of a PDF that matched an in-book search. */
data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
    val matchCount: Int
)

/** Progress of the one-time "prepare this document" pass. */
sealed interface PdfIndexProgress {
    data class Working(val done: Int, val total: Int) : PdfIndexProgress
    data class Complete(
        val pagesWithText: Int,
        val total: Int,
        val capability: PdfTextCapability
    ) : PdfIndexProgress

    data class Failed(val message: String) : PdfIndexProgress
}

/**
 * Owns the per-page text index of a PDF.
 *
 * A PDF stores glyphs and positions, not sentences, so every feature that needs *words* — reflowed
 * text mode, narration, whole-book search, word selection on old devices — depends on an
 * extraction pass. Doing that lazily per swipe is slow and repetitive, and doing it during import
 * blocks the library, so it happens once, up front, with visible progress, and is then persisted.
 *
 * The pass is resumable: pages already in the database are skipped, so a cancelled or interrupted
 * run costs only the pages it had not reached.
 */
class PdfTextIndexer(private val context: Context) {

    private val dao = AppDatabase.getDatabase(context).pdfPageTextDao()

    suspend fun indexedPageCount(bookId: Long): Int = withContext(Dispatchers.IO) {
        dao.countIndexedPages(bookId)
    }

    fun observeIndexedPageCount(bookId: Long): Flow<Int> = dao.observeIndexedPageCount(bookId)

    suspend fun pagesWithText(bookId: Long): Int = withContext(Dispatchers.IO) {
        dao.countPagesWithText(bookId)
    }

    suspend fun pageText(bookId: Long, pageIndex: Int): String? = withContext(Dispatchers.IO) {
        dao.getPageText(bookId, pageIndex)
    }

    /** True when every page of the book has been visited by the extraction pass. */
    suspend fun isIndexed(bookId: Long, totalPages: Int): Boolean =
        totalPages > 0 && indexedPageCount(bookId) >= totalPages

    suspend fun search(bookId: Long, query: String, limit: Int = 80): List<PdfSearchHit> =
        withContext(Dispatchers.IO) {
            if (query.length < 2) return@withContext emptyList()
            dao.searchPages(bookId, query, limit).mapNotNull { page ->
                val snippet = SearchSnippets.build(page.text, query) ?: return@mapNotNull null
                PdfSearchHit(
                    pageIndex = page.pageIndex,
                    snippet = snippet,
                    matchCount = SearchSnippets.countMatches(page.text, query)
                )
            }
        }

    suspend fun clear(bookId: Long) = withContext(Dispatchers.IO) {
        dao.deleteForBook(bookId)
    }

    fun index(book: Book, totalPagesHint: Int): Flow<PdfIndexProgress> = flow {
        val engine = PdfTextEngines.open(context, Uri.parse(book.filePath), totalPagesHint)
        if (engine == null) {
            emit(PdfIndexProgress.Failed("This PDF could not be opened for text extraction."))
            return@flow
        }

        try {
            val total = (if (engine.pageCount > 0) engine.pageCount else totalPagesHint)
                .coerceAtLeast(1)
            val alreadyIndexed = dao.getIndexedPageIndices(book.id).toHashSet()
            var done = alreadyIndexed.size
            emit(PdfIndexProgress.Working(done, total))

            val batch = ArrayList<PdfPageTextEntity>(BATCH_SIZE)
            for (pageIndex in 0 until total) {
                if (pageIndex in alreadyIndexed) continue
                currentCoroutineContext().ensureActive()

                val text = runCatching { engine.pageText(pageIndex)?.text }
                    .getOrNull()
                    .orEmpty()
                batch += PdfPageTextEntity(
                    bookId = book.id,
                    pageIndex = pageIndex,
                    text = text
                )
                done++

                if (batch.size >= BATCH_SIZE) {
                    dao.upsertAll(batch)
                    batch.clear()
                    emit(PdfIndexProgress.Working(done, total))
                }
            }
            if (batch.isNotEmpty()) {
                dao.upsertAll(batch)
                emit(PdfIndexProgress.Working(done, total))
            }

            emit(
                PdfIndexProgress.Complete(
                    pagesWithText = dao.countPagesWithText(book.id),
                    total = total,
                    capability = engine.capability
                )
            )
        } finally {
            runCatching { engine.close() }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** Pages per database write: small enough to show smooth progress, large enough to be cheap. */
        const val BATCH_SIZE = 8
    }
}
