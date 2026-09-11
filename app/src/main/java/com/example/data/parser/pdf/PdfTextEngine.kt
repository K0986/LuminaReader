package com.example.data.parser.pdf

import android.content.Context
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.content.PdfPageTextContent
import android.graphics.pdf.models.PageMatchBounds
import android.graphics.pdf.models.selection.PageSelection
import android.graphics.pdf.models.selection.SelectionBoundary
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.data.parser.PdfBookParser
import com.example.data.parser.PdfTextExtractor

/**
 * Read-only access to the *text layer* of a PDF: the words, where they sit on the page, and the
 * selections and search hits the platform can resolve for us.
 *
 * Rendering (turning a page into a bitmap) stays in [PdfBookParser.PdfDocumentRenderer]; this class
 * is only about text, and the two hold independent file descriptors so a slow text pass never
 * blocks page rendering.
 */
interface PdfTextEngine : AutoCloseable {

    val pageCount: Int

    val capability: PdfTextCapability

    fun pageSize(pageIndex: Int): PdfPageSize?

    /** All text on a page, with glyph boxes when the platform provides them. */
    fun pageText(pageIndex: Int): PdfPageText?

    /** The word under a point on the page, or null when there is no text there. */
    fun wordAt(pageIndex: Int, point: PdfPoint): PdfSelection?

    /** Re-resolves a selection from platform character indices (used while dragging handles). */
    fun selectRange(pageIndex: Int, startChar: Int, endChar: Int): PdfSelection?

    fun search(pageIndex: Int, query: String): List<PdfPageMatch>
}

object PdfTextEngines {

    private const val TAG = "PdfTextEngines"

    /** SDK extension version of Android 12 that first shipped `PdfRendererPreV`. */
    private const val REQUIRED_S_EXTENSION = 13

    /**
     * Opens the best available engine for [uri].
     *
     * Newer devices get the platform text layer (real glyph boxes, word selection and search);
     * older ones fall back to the in-app content-stream parser, which can read words but not their
     * position.
     */
    fun open(context: Context, uri: Uri, pageCountHint: Int = 0): PdfTextEngine? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            platformEngine(context, uri) { PlatformPdfTextEngine(it, ModernDocument(it)) }?.let { return it }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPreVExtension()) {
            platformEngine(context, uri) { PlatformPdfTextEngine(it, PreVDocument(it)) }?.let { return it }
        }
        return LegacyPdfTextEngine(context, uri, pageCountHint)
    }

    private fun hasPreVExtension(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= REQUIRED_S_EXTENSION

    private inline fun platformEngine(
        context: Context,
        uri: Uri,
        factory: (ParcelFileDescriptor) -> PdfTextEngine
    ): PdfTextEngine? {
        val pfd = PdfBookParser.openFileDescriptor(context, uri) ?: return null
        return try {
            factory(pfd)
        } catch (t: Throwable) {
            Log.w(TAG, "Platform PDF text layer unavailable for $uri", t)
            try {
                pfd.close()
            } catch (ignored: Throwable) {
            }
            null
        }
    }
}

/**
 * A page opened for text queries. The two platform renderers expose identical text APIs on
 * unrelated types, so they are adapted to this tiny interface instead of being special-cased
 * throughout the engine.
 */
private interface TextPage : AutoCloseable {
    val widthPt: Int
    val heightPt: Int
    fun textContents(): List<PdfPageTextContent>
    fun select(start: SelectionBoundary, stop: SelectionBoundary): PageSelection?
    fun search(query: String): List<PageMatchBounds>
}

private interface TextDocument : AutoCloseable {
    val pageCount: Int
    fun openPage(pageIndex: Int): TextPage
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class ModernDocument(pfd: ParcelFileDescriptor) : TextDocument {
    private val renderer = PdfRenderer(pfd)
    override val pageCount: Int get() = renderer.pageCount
    override fun openPage(pageIndex: Int): TextPage = Page(renderer.openPage(pageIndex))
    override fun close() = renderer.close()

    private class Page(private val page: PdfRenderer.Page) : TextPage {
        override val widthPt: Int get() = page.width
        override val heightPt: Int get() = page.height
        override fun textContents(): List<PdfPageTextContent> = page.textContents
        override fun select(start: SelectionBoundary, stop: SelectionBoundary): PageSelection? =
            page.selectContent(start, stop)

        override fun search(query: String): List<PageMatchBounds> = page.searchText(query)
        override fun close() = page.close()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class PreVDocument(pfd: ParcelFileDescriptor) : TextDocument {
    private val renderer = PdfRendererPreV(pfd)
    override val pageCount: Int get() = renderer.pageCount
    override fun openPage(pageIndex: Int): TextPage = Page(renderer.openPage(pageIndex))
    override fun close() = renderer.close()

    private class Page(private val page: PdfRendererPreV.Page) : TextPage {
        override val widthPt: Int get() = page.width
        override val heightPt: Int get() = page.height
        override fun textContents(): List<PdfPageTextContent> = page.textContents
        override fun select(start: SelectionBoundary, stop: SelectionBoundary): PageSelection? =
            page.selectContent(start, stop)

        override fun search(query: String): List<PageMatchBounds> = page.searchText(query)
        override fun close() = page.close()
    }
}

/** Engine backed by the platform PDF text APIs. */
private class PlatformPdfTextEngine(
    private val pfd: ParcelFileDescriptor,
    private val document: TextDocument
) : PdfTextEngine {

    private val lock = Any()
    private var closed = false

    override val pageCount: Int = document.pageCount

    override val capability: PdfTextCapability = PdfTextCapability.TEXT_AND_SELECTION

    override fun pageSize(pageIndex: Int): PdfPageSize? = withPage(pageIndex) { page ->
        PdfPageSize(page.widthPt.toFloat(), page.heightPt.toFloat())
    }

    override fun pageText(pageIndex: Int): PdfPageText? = withPage(pageIndex) { page ->
        PdfPageText(pageIndex, page.textContents().map { it.toBlock() })
    }

    override fun wordAt(pageIndex: Int, point: PdfPoint): PdfSelection? = withPage(pageIndex) { page ->
        val boundary = SelectionBoundary(Point(point.x.toInt(), point.y.toInt()))
        page.select(boundary, boundary)?.toSelection(pageIndex)
    }

    override fun selectRange(pageIndex: Int, startChar: Int, endChar: Int): PdfSelection? =
        withPage(pageIndex) { page ->
            val from = minOf(startChar, endChar).coerceAtLeast(0)
            val to = maxOf(startChar, endChar).coerceAtLeast(from)
            page.select(SelectionBoundary(from), SelectionBoundary(to))?.toSelection(pageIndex)
        }

    override fun search(pageIndex: Int, query: String): List<PdfPageMatch> {
        if (query.isBlank()) return emptyList()
        return withPage(pageIndex) { page ->
            page.search(query).map { match ->
                PdfPageMatch(
                    pageIndex = pageIndex,
                    startChar = match.textStartIndex,
                    rects = match.bounds.map { it.toPdfRect() }
                )
            }
        } ?: emptyList()
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { document.close() }
            runCatching { pfd.close() }
        }
    }

    private fun <T> withPage(pageIndex: Int, block: (TextPage) -> T): T? {
        if (pageIndex < 0 || pageIndex >= pageCount) return null
        synchronized(lock) {
            if (closed) return null
            var page: TextPage? = null
            return try {
                page = document.openPage(pageIndex)
                block(page)
            } catch (t: Throwable) {
                Log.w("PdfTextEngine", "Text query failed on page $pageIndex", t)
                null
            } finally {
                runCatching { page?.close() }
            }
        }
    }
}

/**
 * Fallback engine for devices without the platform text layer. It can read words (via the in-app
 * content-stream parser) but has no glyph boxes, so on-page selection is not offered.
 */
private class LegacyPdfTextEngine(
    private val context: Context,
    private val uri: Uri,
    pageCountHint: Int
) : PdfTextEngine {

    override val pageCount: Int = pageCountHint.coerceAtLeast(0)

    override val capability: PdfTextCapability = PdfTextCapability.TEXT_ONLY

    private val pages: List<PdfTextExtractor.ExtractedPage> by lazy {
        if (pageCount <= 0) {
            emptyList()
        } else {
            runCatching {
                PdfTextExtractor.extractPages(
                    context = context,
                    uri = uri,
                    pageCount = pageCount,
                    maxPages = LEGACY_MAX_PAGES,
                    maxFileSizeBytes = LEGACY_MAX_BYTES
                )
            }.getOrDefault(emptyList())
        }
    }

    override fun pageSize(pageIndex: Int): PdfPageSize? = null

    override fun pageText(pageIndex: Int): PdfPageText? {
        val page = pages.getOrNull(pageIndex) ?: return null
        return PdfPageText(pageIndex, listOf(PdfTextBlock(page.text)))
    }

    override fun wordAt(pageIndex: Int, point: PdfPoint): PdfSelection? = null

    override fun selectRange(pageIndex: Int, startChar: Int, endChar: Int): PdfSelection? = null

    override fun search(pageIndex: Int, query: String): List<PdfPageMatch> = emptyList()

    override fun close() = Unit

    private companion object {
        /**
         * The legacy parser reads the whole file into memory, so it stays behind generous but firm
         * limits. Books past these limits simply have no text layer on old devices.
         */
        const val LEGACY_MAX_PAGES = 250
        const val LEGACY_MAX_BYTES = 12L * 1024 * 1024
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun PdfPageTextContent.toBlock(): PdfTextBlock =
    PdfTextBlock(text = text, rects = bounds.map { it.toPdfRect() })

@RequiresApi(Build.VERSION_CODES.S)
private fun PageSelection.toSelection(pageIndex: Int): PdfSelection? {
    val contents = selectedTextContents
    if (contents.isEmpty()) return null
    val text = contents.joinToString(" ") { it.text }.trim()
    if (text.isBlank()) return null
    return PdfSelection(
        pageIndex = pageIndex,
        text = text,
        startChar = start.index,
        endChar = stop.index,
        rects = contents.flatMap { content -> content.bounds.map { it.toPdfRect() } }
    )
}

private fun RectF.toPdfRect(): PdfRect = PdfRect(left, top, right, bottom)
