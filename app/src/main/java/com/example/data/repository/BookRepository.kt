package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Book
import com.example.data.model.Bookmark
import com.example.data.model.Collection
import com.example.data.model.Highlight
import com.example.data.parser.EpubParser
import com.example.data.parser.ParsedBook
import com.example.data.parser.PdfBookParser
import com.example.data.parser.TxtBookParser
import com.example.data.sample.SampleBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** A book that has been opened and verified, with what the reader needs to know. */
data class PreparedBook(
    val book: Book,
    val pageCount: Int,
    val hasTextLayer: Boolean
)

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val highlightDao = db.highlightDao()
    private val collectionDao = db.collectionDao()

    /**
     * Parsed content cache.
     *
     * Was a plain `mutableMapOf`, mutated from several coroutines and never evicted --
     * so it both risked concurrent modification and retained every book's full text plus
     * its cover [Bitmap] for the life of the process. A bounded, concurrent cache keeps
     * the hot book fast without the leak.
     */
    private val parsedBookCache = object : LinkedHashMap<Long, ParsedBook>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ParsedBook>) = size > 4
    }

    private fun cachedBook(id: Long): ParsedBook? = synchronized(parsedBookCache) { parsedBookCache[id] }

    private fun cacheBook(id: Long, parsed: ParsedBook) {
        synchronized(parsedBookCache) { parsedBookCache[id] = parsed }
    }

    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allCollections: Flow<List<Collection>> = collectionDao.getAllCollections()
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val allHighlights: Flow<List<Highlight>> = highlightDao.getAllHighlights()

    suspend fun initializeLibraryIfEmpty() = withContext(Dispatchers.IO) {
        val existing = allBooks.first()
        if (existing.isEmpty()) {
            val samples = SampleBooks.seedDefaultBooks(context)
            for (pair in samples) {
                val insertedId = bookDao.insertBook(pair.first)
                cacheBook(insertedId, pair.second)
            }
        }
    }

    fun getBookById(id: Long): Flow<Book?> = bookDao.getBookById(id)

    suspend fun getBookByIdDirect(id: Long): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookByIdDirect(id)
    }

    suspend fun loadBookContent(book: Book): ParsedBook = withContext(Dispatchers.IO) {
        cachedBook(book.id)?.let { return@withContext it }

        val uri = Uri.parse(book.filePath)
        val parsed = when (book.format.uppercase()) {
            "EPUB" -> {
                // If it's a sample book cached in memory
                if (book.filePath.contains("sherlock_holmes")) {
                    SampleBooks.getSherlockHolmes()
                } else if (book.filePath.contains("alice_wonderland")) {
                    SampleBooks.getAliceInWonderland()
                } else {
                    EpubParser.parse(context, uri)
                }
            }
            "PDF" -> {
                PdfBookParser.parse(context, uri, book.title)
            }
            "TXT" -> {
                if (book.filePath.contains("frankenstein")) {
                    SampleBooks.getFrankenstein()
                } else {
                    TxtBookParser.parse(context, uri, book.title)
                }
            }
            else -> {
                EpubParser.parse(context, uri)
            }
        }
        cacheBook(book.id, parsed)
        parsed
    }

    /**
     * Gets a book ready to be displayed, *before* the reader screen is shown.
     *
     * Previously the reader itself did this work: you tapped a book, landed on an empty
     * page, and watched "Loading book pages..." while the file was opened, the page count
     * read and the text extracted. For a large PDF that meant several seconds of blank
     * screen inside the reader, and if the file turned out to be unopenable you were
     * already stranded there with a spinner.
     *
     * Doing it here means the library can show progress, report a failure in place, and
     * only navigate once the document is genuinely ready.
     */
    suspend fun prepareBook(book: Book): Result<PreparedBook> = withContext(Dispatchers.IO) {
        try {
            val parsed = loadBookContent(book)

            if (book.format.equals("PDF", ignoreCase = true)) {
                val renderer = PdfBookParser.createRenderer(context, Uri.parse(book.filePath))
                    ?: return@withContext Result.failure(
                        IllegalStateException(
                            "This PDF could not be opened. It may be corrupt, password " +
                                "protected, or no longer at its original location."
                        )
                    )
                renderer.use { session ->
                    // Warm the first page's text and image so the reader opens onto
                    // content rather than a spinner.
                    val firstPageText = session.pageText(0)
                    session.renderPage(0, targetWidth = 1080)
                    return@withContext Result.success(
                        PreparedBook(
                            book = book,
                            pageCount = session.pageCount,
                            hasTextLayer = firstPageText.hasText
                        )
                    )
                }
            }

            if (parsed.chapters.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No readable content was found in this book.")
                )
            }

            Result.success(
                PreparedBook(
                    book = book,
                    pageCount = parsed.chapters.size,
                    hasTextLayer = true
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun importBookFromUri(uri: Uri, displayName: String? = null): Result<Book> = withContext(Dispatchers.IO) {
        try {
            val resolvedName = displayName ?: getFileNameFromUri(uri) ?: "Unknown Book"
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val format = when {
                mimeType == "application/pdf" || resolvedName.endsWith(".pdf", ignoreCase = true) -> "PDF"
                mimeType == "application/epub+zip" || resolvedName.endsWith(".epub", ignoreCase = true) -> "EPUB"
                mimeType == "text/plain" || resolvedName.endsWith(".txt", ignoreCase = true) -> "TXT"
                resolvedName.endsWith(".cbz", ignoreCase = true) -> "CBZ"
                else -> "EPUB"
            }

            // Copy file to app internal storage or keep persistent URI
            val hash = computeUriHash(uri)
            val duplicate = bookDao.getBookByHash(hash)
            if (duplicate != null) {
                return@withContext Result.failure(Exception("This book is already in your library ('${duplicate.title}')"))
            }

            // Copy file to app internal storage for guaranteed permanent seekable access
            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) booksDir.mkdirs()
            val safeExt = format.lowercase()
            val localBookFile = File(booksDir, "book_${System.currentTimeMillis()}.$safeExt")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localBookFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // Ignore and fallback
            }

            val persistentUri = if (localBookFile.exists() && localBookFile.length() > 0L) {
                Uri.fromFile(localBookFile)
            } else {
                uri
            }

            // Parse metadata and cover
            val parsed = when (format) {
                "EPUB" -> EpubParser.parse(context, persistentUri)
                "PDF" -> PdfBookParser.parse(context, persistentUri, resolvedName.substringBeforeLast("."))
                "TXT" -> TxtBookParser.parse(context, persistentUri, resolvedName.substringBeforeLast("."))
                else -> EpubParser.parse(context, persistentUri)
            }

            val finalTitle = if (parsed.title.isNotBlank() && parsed.title != "Untitled Book") parsed.title else resolvedName.substringBeforeLast(".")
            val finalAuthor = if (parsed.author.isNotBlank()) parsed.author else "Unknown Author"

            // Save cover thumbnail
            var coverPath: String? = null
            val coverBitmap = parsed.coverBitmap ?: SampleBooks.createSampleCover(
                finalTitle,
                finalAuthor,
                android.graphics.Color.parseColor("#1E293B"),
                android.graphics.Color.parseColor("#38BDF8")
            )
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.png")
            FileOutputStream(coverFile).use { out ->
                coverBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                coverPath = coverFile.absolutePath
            }

            val book = Book(
                filePath = persistentUri.toString(),
                title = finalTitle,
                author = finalAuthor,
                format = format,
                coverPath = coverPath,
                dateAdded = System.currentTimeMillis(),
                lastOpened = 0L,
                progressPercent = 0.0f,
                progressLocation = "0",
                readingStatus = "UNREAD",
                fileHash = hash,
                totalPages = parsed.totalPagesEstimate,
                fileSizeBytes = getFileSize(uri),
                currentChapterTitle = parsed.tableOfContents.firstOrNull()?.title ?: ""
            )

            val newId = bookDao.insertBook(book)
            val insertedBook = book.copy(id = newId)
            cacheBook(newId, parsed)
            Result.success(insertedBook)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProgress(
        bookId: Long,
        percent: Float,
        location: String,
        chapterTitle: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        bookDao.updateProgress(
            id = bookId,
            percent = percent.coerceIn(0f, 100f),
            location = location,
            chapterTitle = chapterTitle,
            lastOpened = System.currentTimeMillis(),
            status = status
        )
    }

    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        bookDao.toggleFavorite(bookId, isFavorite)
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.deleteBook(book)
        synchronized(parsedBookCache) { parsedBookCache.remove(book.id) }
        book.coverPath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }

    // Bookmarks
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.getBookmarksForBook(bookId)

    suspend fun addBookmark(bookmark: Bookmark): Long = withContext(Dispatchers.IO) {
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun removeBookmark(id: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBookmarkById(id)
    }

    // Highlights
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> = highlightDao.getHighlightsForBook(bookId)

    suspend fun addHighlight(highlight: Highlight): Long = withContext(Dispatchers.IO) {
        highlightDao.insertHighlight(highlight)
    }

    suspend fun updateHighlight(highlight: Highlight) = withContext(Dispatchers.IO) {
        highlightDao.updateHighlight(highlight)
    }

    suspend fun removeHighlight(id: Long) = withContext(Dispatchers.IO) {
        highlightDao.deleteHighlightById(id)
    }

    // Collections
    suspend fun createCollection(name: String): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(Collection(name = name))
    }

    suspend fun deleteCollection(collection: Collection) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(collection)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {}
        return name ?: uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").length()
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun computeUriHash(uri: Uri): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val stream: InputStream? = if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            // The previous version hashed only the first 64 KB. Two different books
            // produced from the same template share that prefix, and because `fileHash`
            // carries a UNIQUE index the second import was rejected as "already in your
            // library". Hashing the whole file makes the identity check honest; a book
            // is a few megabytes, so this costs milliseconds.
            stream?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "hash_${uri.toString().hashCode()}_${System.currentTimeMillis()}"
        }
    }

    suspend fun downloadAndImportPdf(
        urlString: String = "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf",
        customTitle: String? = null
    ): Result<Book> = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error $responseCode: ${connection.responseMessage}"))
            }

            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) booksDir.mkdirs()
            val cleanName = customTitle?.replace(Regex("[^a-zA-Z0-9_]"), "_")?.lowercase()
                ?: urlString.substringAfterLast("/").substringBefore("?").takeIf { it.endsWith(".pdf", ignoreCase = true) }
                ?: "downloaded_pdf_${System.currentTimeMillis()}.pdf"
            val targetName = if (cleanName.endsWith(".pdf", ignoreCase = true)) cleanName else "$cleanName.pdf"
            val localFile = File(booksDir, targetName)

            connection.inputStream.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }

            importBookFromUri(Uri.fromFile(localFile), displayName = customTitle ?: targetName.removeSuffix(".pdf").replace("_", " "))
        } catch (t: Throwable) {
            Result.failure(Exception("Download failed: ${t.localizedMessage}", t))
        }
    }

    /**
     * Scans device downloads, documents, and media storage for ebooks (e.g. Java books, PDFs)
     * and automatically imports them into the library.
     */
    suspend fun scanAndImportDeviceDownloads(): List<Book> = withContext(Dispatchers.IO) {
        val importedBooks = mutableListOf<Book>()
        val candidateFiles = mutableListOf<File>()

        // 1. Scan standard device Download & Document directories
        val scanDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Downloads"),
            File("/sdcard/Documents"),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in scanDirs) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val name = file.name.lowercase()
                        if (file.isFile && file.length() > 0 &&
                            (name.endsWith(".pdf") || name.endsWith(".epub") || name.endsWith(".txt"))
                        ) {
                            candidateFiles.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("BookRepository", "Directory scan notice: ${e.message}")
            }
        }

        // 2. Query MediaStore for downloaded ebooks and documents
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA
            )
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.epub' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.txt'"
            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                null
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = if (nameCol != -1) c.getString(nameCol) else null
                    val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                    if (dataPath != null) {
                        val file = File(dataPath)
                        if (file.exists() && file.isFile) {
                            candidateFiles.add(file)
                        }
                    } else if (name != null) {
                        val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                        val res = importBookFromUri(contentUri, name)
                        res.getOrNull()?.let { importedBooks.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BookRepository", "MediaStore scan note: ${e.message}")
        }

        // Import all discovered candidate files
        val uniqueFiles = candidateFiles.distinctBy { it.absolutePath }
        for (file in uniqueFiles) {
            try {
                val res = importBookFromUri(Uri.fromFile(file), file.name)
                res.getOrNull()?.let { importedBooks.add(it) }
            } catch (e: Exception) {
                Log.w("BookRepository", "Discovered file import note: ${file.name}", e)
            }
        }

        importedBooks
    }
}
