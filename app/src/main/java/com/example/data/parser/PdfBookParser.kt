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

object PdfBookParser {

    private const val TAG = "PdfBookParser"

    /**
     * Resolves a ParcelFileDescriptor from any URI (file, content, or absolute path)
     * with an automatic seekable cache fallback.
     * PdfRenderer REQUIRES a seekable file descriptor.
     */
    fun openFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
        return try {
            val uriStr = uri.toString()
            when {
                uri.scheme == "file" || uri.scheme.isNullOrEmpty() -> {
                    val rawPath = uri.path ?: uriStr.removePrefix("file://")
                    val decodedPath = Uri.decode(rawPath)
                    val file = File(decodedPath).takeIf { it.exists() } ?: File(rawPath)
                    if (file.exists() && file.length() > 0L) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        copyToCacheAndOpen(context, uri)
                    }
                }
                uri.scheme == "content" -> {
                    copyToCacheAndOpen(context, uri)
                }
                else -> {
                    val file = File(uriStr)
                    if (file.exists() && file.length() > 0L) {
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
            val cacheDir = File(context.cacheDir, "pdf_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val safeHash = uri.toString().hashCode().toUInt()
            val cacheFile = File(cacheDir, "pdf_$safeHash.pdf")

            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: java.io.FileInputStream(File(uri.path ?: uri.toString()))
                inputStream.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (cacheFile.exists() && cacheFile.length() > 0L) {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to copy PDF to cache: $uri", t)
            null
        }
    }

    fun parse(context: Context, uri: Uri, fallbackTitle: String): ParsedBook {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var coverBitmap: Bitmap? = null
        var pageCount = 1

        try {
            pfd = openFileDescriptor(context, uri)
            if (pfd != null) {
                try {
                    renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount.coerceAtLeast(1)

                    // Render page 0 as cover thumbnail
                    if (pageCount > 0) {
                        var page: PdfRenderer.Page? = null
                        try {
                            page = renderer.openPage(0)
                            val pw = if (page.width > 0) page.width else 595
                            val ph = if (page.height > 0) page.height else 842
                            val width = 360
                            val height = ((width.toFloat() * ph) / pw).toInt().coerceIn(360, 640)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            coverBitmap = bitmap
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to render PDF cover", t)
                        } finally {
                            try { page?.close() } catch (ignored: Throwable) {}
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "PdfRenderer initialization failed in parse", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PDF parse error", t)
        } finally {
            try { renderer?.close() } catch (ignored: Throwable) {}
            try { pfd?.close() } catch (ignored: Throwable) {}
        }

        val toc = ArrayList<TocItem>(pageCount.coerceAtMost(2000))
        val chapters = ArrayList<SpineChapter>(pageCount.coerceAtMost(2000))

        // Only run stream extraction on small PDFs (<= 30 pages) to prevent OOM on 500+ page books
        val extractedPages = if (pageCount <= 30) {
            try {
                PdfTextExtractor.extractPages(context, uri, pageCount)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to extract text from PDF", t)
                emptyList()
            }
        } else {
            emptyList()
        }

        for (i in 0 until pageCount) {
            val pageNum = i + 1
            val pageData = extractedPages.getOrNull(i)
            val pageText = pageData?.text?.takeIf { it.isNotBlank() } ?: "Page $pageNum of $pageCount"
            val paragraphs = pageData?.paragraphs?.takeIf { it.isNotEmpty() } ?: listOf(pageText)
            val chapterTitle = "Page $pageNum"

            toc.add(TocItem("pdf_page_$i", chapterTitle, i))
            chapters.add(
                SpineChapter(
                    id = "pdf_page_$i",
                    title = chapterTitle,
                    plainText = pageText,
                    formattedParagraphs = paragraphs,
                    wordCount = 1
                )
            )
        }

        return ParsedBook(
            title = fallbackTitle,
            author = "PDF Document",
            format = "PDF",
            coverBitmap = coverBitmap,
            tableOfContents = toc,
            chapters = chapters,
            totalPagesEstimate = pageCount
        )
    }

    /**
     * Creates a high-performance, cached PdfDocumentRenderer session for reading.
     */
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
     * Thread-safe, memory-bounded, cached PDF document renderer that keeps an open PdfRenderer
     * for seamless swiping and scrolling without re-opening file descriptors.
     */
    class PdfDocumentRenderer(
        private val context: Context,
        private val uri: Uri
    ) : AutoCloseable {
        private val lock = Any()
        private var pfd: ParcelFileDescriptor? = null
        private var renderer: PdfRenderer? = null

        // Cache sized by memory in KB (up to 24MB max cache to prevent device heap exhaustion)
        private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheLimitKb = (maxMemoryKb / 10).coerceIn(12 * 1024, 24 * 1024)
        private val bitmapCache = object : LruCache<Int, Bitmap>(cacheLimitKb) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }

        var pageCount: Int = 0
            private set

        init {
            openDocument()
        }

        private fun openDocument() {
            synchronized(lock) {
                try {
                    pfd = openFileDescriptor(context, uri)
                    if (pfd != null) {
                        renderer = PdfRenderer(pfd!!)
                        pageCount = renderer!!.pageCount
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to open PDF renderer", t)
                    pageCount = 0
                }
            }
        }

        fun renderPage(pageIndex: Int, targetWidth: Int = 960): Bitmap? {
            if (pageIndex !in 0 until pageCount) return null
            synchronized(lock) {
                bitmapCache.get(pageIndex)?.let { return it }
                val currentRenderer = renderer ?: return null
                var page: PdfRenderer.Page? = null
                return try {
                    page = currentRenderer.openPage(pageIndex)
                    val pw = if (page.width > 0) page.width else 595
                    val ph = if (page.height > 0) page.height else 842
                    val width = targetWidth.coerceIn(360, 1080)
                    val height = ((width.toFloat() * ph) / pw).toInt().coerceIn(360, 2160)

                    var bitmap: Bitmap? = null
                    try {
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    } catch (oom: OutOfMemoryError) {
                        Log.w(TAG, "OOM in createBitmap for page $pageIndex, clearing cache and downsizing", oom)
                        bitmapCache.evictAll()
                        System.gc()
                        try {
                            val smallerWidth = (width * 0.7f).toInt().coerceAtLeast(360)
                            val smallerHeight = ((smallerWidth.toFloat() * ph) / pw).toInt().coerceIn(360, 1600)
                            bitmap = Bitmap.createBitmap(smallerWidth, smallerHeight, Bitmap.Config.ARGB_8888)
                        } catch (secondOom: OutOfMemoryError) {
                            Log.e(TAG, "Secondary OOM in createBitmap, skipping page", secondOom)
                            return null
                        }
                    }

                    if (bitmap != null) {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapCache.put(pageIndex, bitmap)
                    }
                    bitmap
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to render PDF page $pageIndex", t)
                    null
                } finally {
                    try {
                        page?.close()
                    } catch (ignored: Throwable) {}
                }
            }
        }

        override fun close() {
            synchronized(lock) {
                try { renderer?.close() } catch (ignored: Throwable) {}
                try { pfd?.close() } catch (ignored: Throwable) {}
                try { bitmapCache.evictAll() } catch (ignored: Throwable) {}
                renderer = null
                pfd = null
                pageCount = 0
            }
        }
    }
}

