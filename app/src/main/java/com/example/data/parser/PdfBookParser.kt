package com.example.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object PdfBookParser {

    private const val TAG = "PdfBookParser"

    /**
     * Resolves a seekable [ParcelFileDescriptor] for any URI, copying to the cache
     * directory when the source is not already a real file. [PdfRenderer] requires a
     * seekable descriptor, which `content://` providers do not always give us.
     */
    fun openFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
        return try {
            when (uri.scheme) {
                "content" -> copyToCacheAndOpen(context, uri)
                "file", null, "" -> {
                    val rawPath = uri.path ?: uri.toString().removePrefix("file://")
                    val file = File(Uri.decode(rawPath)).takeIf { it.exists() } ?: File(rawPath)
                    if (file.isFile && file.length() > 0L) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        copyToCacheAndOpen(context, uri)
                    }
                }
                else -> {
                    val file = File(uri.toString())
                    if (file.isFile && file.length() > 0L) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        copyToCacheAndOpen(context, uri)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error opening file descriptor for URI: $uri", t)
            null
        }
    }

    private fun copyToCacheAndOpen(context: Context, uri: Uri): ParcelFileDescriptor? {
        return try {
            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val cacheFile = File(cacheDir, "pdf_${uri.toString().hashCode().toUInt()}.pdf")

            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: File(uri.path ?: uri.toString()).takeIf { it.exists() }?.inputStream()
                    ?: return null
                input.use { source ->
                    FileOutputStream(cacheFile).use { source.copyTo(it) }
                }
            }

            if (cacheFile.length() > 0L) {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to copy PDF to cache: $uri", t)
            null
        }
    }

    /**
     * Reads only what the library screen needs: the page count and a cover thumbnail.
     *
     * Notably this no longer fabricates one [SpineChapter] per page holding the string
     * "Page 7 of 412". Page text is fetched on demand by [PdfDocumentRenderer.pageText],
     * which means opening a 400-page book costs the same as opening a 4-page one.
     */
    fun parse(context: Context, uri: Uri, fallbackTitle: String): ParsedBook {
        var coverBitmap: Bitmap? = null
        var pageCount = 0

        try {
            openFileDescriptor(context, uri)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        runCatching {
                            renderer.openPage(0).use { page ->
                                coverBitmap = renderToBitmap(page, targetWidth = 360)
                            }
                        }.onFailure { Log.w(TAG, "Failed to render PDF cover", it) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PDF parse error", t)
        }

        val toc = (0 until pageCount).map { TocItem("pdf_page_$it", "Page ${it + 1}", it) }

        return ParsedBook(
            title = fallbackTitle,
            author = "PDF Document",
            format = "PDF",
            coverBitmap = coverBitmap,
            tableOfContents = toc,
            chapters = emptyList(),
            totalPagesEstimate = pageCount.coerceAtLeast(1)
        )
    }

    private fun renderToBitmap(page: PdfRenderer.Page, targetWidth: Int): Bitmap {
        val pw = if (page.width > 0) page.width else 595
        val ph = if (page.height > 0) page.height else 842
        val height = ((targetWidth.toFloat() * ph) / pw).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    fun createRenderer(context: Context, uri: Uri): PdfDocumentRenderer? {
        return try {
            val renderer = PdfDocumentRenderer(context.applicationContext, uri)
            if (renderer.pageCount > 0) renderer else {
                renderer.close()
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in createRenderer", t)
            null
        }
    }

    /**
     * A long-lived PDF session: one open descriptor, a memory-bounded bitmap cache, and
     * lazily extracted page text.
     */
    class PdfDocumentRenderer(
        private val context: Context,
        private val uri: Uri
    ) : AutoCloseable {

        /** Guards [renderer]; [PdfRenderer] permits only one open page at a time. */
        private val renderLock = Any()
        private var pfd: ParcelFileDescriptor? = null
        private var renderer: PdfRenderer? = null

        private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheLimitKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 48 * 1024)

        /**
         * Keyed by page *and* width. The old cache keyed on page alone, so a page first
         * rendered at fit-width resolution stayed cached at that resolution -- zooming in
         * just scaled those pixels up, which is why zoomed pages looked soft.
         */
        private val bitmapCache = object : LruCache<PageKey, Bitmap>(cacheLimitKb) {
            override fun sizeOf(key: PageKey, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
        }

        private val textCache = ConcurrentHashMap<Int, PdfPageText>()

        private data class PageKey(val pageIndex: Int, val width: Int)

        var pageCount: Int = 0
            private set

        init {
            synchronized(renderLock) {
                try {
                    pfd = openFileDescriptor(context, uri)
                    pfd?.let {
                        renderer = PdfRenderer(it)
                        pageCount = renderer?.pageCount ?: 0
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to open PDF renderer", t)
                    pageCount = 0
                }
            }
        }

        /** Aspect ratio (height / width) of a page, for sizing placeholders before render. */
        fun pageAspectRatio(pageIndex: Int): Float {
            if (pageIndex !in 0 until pageCount) return DEFAULT_ASPECT
            return synchronized(renderLock) {
                runCatching {
                    renderer?.openPage(pageIndex)?.use { page ->
                        if (page.width > 0) page.height.toFloat() / page.width.toFloat() else DEFAULT_ASPECT
                    } ?: DEFAULT_ASPECT
                }.getOrDefault(DEFAULT_ASPECT)
            }
        }

        fun cachedPage(pageIndex: Int, targetWidth: Int): Bitmap? =
            bitmapCache.get(PageKey(pageIndex, normaliseWidth(targetWidth)))

        /**
         * Renders [pageIndex] at approximately [targetWidth] pixels wide.
         *
         * Widths are snapped to 160 px buckets so that a pinch gesture producing dozens
         * of intermediate scales does not thrash the cache with near-identical bitmaps.
         */
        fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? {
            if (pageIndex !in 0 until pageCount) return null
            val width = normaliseWidth(targetWidth)
            val key = PageKey(pageIndex, width)

            bitmapCache.get(key)?.let { return it }

            synchronized(renderLock) {
                bitmapCache.get(key)?.let { return it }
                val activeRenderer = renderer ?: return null

                return try {
                    activeRenderer.openPage(pageIndex).use { page ->
                        val bitmap = createBitmapWithRetry(page, width) ?: return null
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapCache.put(key, bitmap)
                        bitmap
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to render PDF page $pageIndex at ${width}px", t)
                    null
                }
            }
        }

        private fun createBitmapWithRetry(page: PdfRenderer.Page, width: Int): Bitmap? {
            val pw = if (page.width > 0) page.width else 595
            val ph = if (page.height > 0) page.height else 842

            var attemptWidth = width
            repeat(3) {
                val height = ((attemptWidth.toFloat() * ph) / pw).toInt().coerceAtLeast(1)
                try {
                    return Bitmap.createBitmap(attemptWidth, height, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM allocating ${attemptWidth}x$height page bitmap; backing off", oom)
                    bitmapCache.evictAll()
                    attemptWidth = (attemptWidth * 0.6f).toInt().coerceAtLeast(MIN_WIDTH)
                }
            }
            return null
        }

        /** Page text, extracted on first request and memoised for the session. */
        fun pageText(pageIndex: Int): PdfPageText {
            if (pageIndex !in 0 until pageCount) {
                return PdfPageText(pageIndex, null, emptyList(), PdfPageText.Source.NONE)
            }
            return textCache.getOrPut(pageIndex) {
                PdfTextExtractor.extractPage(context, uri, pageIndex)
            }
        }

        override fun close() {
            synchronized(renderLock) {
                runCatching { renderer?.close() }
                runCatching { pfd?.close() }
                bitmapCache.evictAll()
                textCache.clear()
                renderer = null
                pfd = null
                pageCount = 0
            }
        }

        private companion object {
            const val MIN_WIDTH = 320
            const val MAX_WIDTH = 2560
            const val WIDTH_BUCKET = 160
            const val DEFAULT_ASPECT = 842f / 595f

            fun normaliseWidth(requested: Int): Int {
                val clamped = requested.coerceIn(MIN_WIDTH, MAX_WIDTH)
                return ((clamped + WIDTH_BUCKET - 1) / WIDTH_BUCKET) * WIDTH_BUCKET
            }
        }
    }
}
