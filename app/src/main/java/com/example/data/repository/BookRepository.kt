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

/**
 * Result of trying to add a file to the library.
 *
 * Importing the same book twice is not an error: when a reader shares a PDF they already own, the
 * right behaviour is to open their existing copy (with its progress and highlights intact) rather
 * than to complain.
 */
sealed interface ImportOutcome {
    data class Added(val book: Book) : ImportOutcome
    data class AlreadyInLibrary(val book: Book) : ImportOutcome
    data class Failed(val message: String, val cause: Throwable? = null) : ImportOutcome

    val bookOrNull: Book?
        get() = when (this) {
            is Added -> book
            is AlreadyInLibrary -> book
            is Failed -> null
        }
}

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val highlightDao = db.highlightDao()
    private val collectionDao = db.collectionDao()

    // In-memory cache of loaded book content for fast reader rendering
    private val parsedBookCache = mutableMapOf<Long, ParsedBook>()

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
                parsedBookCache[insertedId] = pair.second
            }
        }
    }

    fun getBookById(id: Long): Flow<Book?> = bookDao.getBookById(id)

    suspend fun getBookByIdDirect(id: Long): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookByIdDirect(id)
    }

    suspend fun loadBookContent(book: Book): ParsedBook = withContext(Dispatchers.IO) {
        parsedBookCache[book.id]?.let { return@withContext it }

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
        parsedBookCache[book.id] = parsed
        parsed
    }

    suspend fun importBook(uri: Uri, displayName: String? = null): ImportOutcome = withContext(Dispatchers.IO) {
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

            val hash = computeUriHash(uri)
            val duplicate = bookDao.getBookByHash(hash)
            if (duplicate != null) {
                return@withContext ImportOutcome.AlreadyInLibrary(duplicate)
            }

            // Copy the file into app storage. A shared content:// URI is only readable for as long
            // as the sending app grants it, so owning a local copy is what makes the book reopenable
            // tomorrow, and gives PdfRenderer the seekable descriptor it requires.
            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) booksDir.mkdirs()
            val safeExt = format.lowercase()
            val localBookFile = File(booksDir, "book_${System.currentTimeMillis()}.$safeExt")
            val copied = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localBookFile).use { output ->
                        input.copyTo(output)
                    }
                }
                localBookFile.exists() && localBookFile.length() > 0L
            } catch (e: Exception) {
                Log.w("BookRepository", "Could not copy ${uri} into library", e)
                false
            }

            if (!copied && uri.scheme == "content") {
                return@withContext ImportOutcome.Failed(
                    "Could not read this file. Ask the sending app to share it again, or copy it to your device first."
                )
            }

            val persistentUri = if (copied) Uri.fromFile(localBookFile) else uri

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
                fileSizeBytes = getFileSize(persistentUri),
                currentChapterTitle = parsed.tableOfContents.firstOrNull()?.title ?: ""
            )

            val newId = bookDao.insertBook(book)
            val insertedBook = book.copy(id = newId)
            parsedBookCache[newId] = parsed
            ImportOutcome.Added(insertedBook)
        } catch (e: Exception) {
            ImportOutcome.Failed(e.message ?: "Failed to import this book", e)
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
        parsedBookCache.remove(book.id)
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
            val md = MessageDigest.getInstance("MD5")
            val stream: InputStream? = if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                var count = 0
                // Hash first 64KB for speed and uniqueness
                while (bytesRead != -1 && count < 65536) {
                    md.update(buffer, 0, bytesRead)
                    count += bytesRead
                    bytesRead = input.read(buffer)
                }
            }
            // Two different books produced by the same tool can share a 64KB header, so the file
            // length is mixed in as well: a false duplicate would silently open the wrong book.
            md.update(getFileSize(uri).toString().toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "hash_${uri.toString().hashCode()}_${System.currentTimeMillis()}"
        }
    }

    suspend fun downloadAndImportPdf(
        urlString: String = "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf",
        customTitle: String? = null
    ): ImportOutcome = withContext(Dispatchers.IO) {
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
                return@withContext ImportOutcome.Failed("HTTP Error $responseCode: ${connection.responseMessage}")
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

            importBook(Uri.fromFile(localFile), displayName = customTitle ?: targetName.removeSuffix(".pdf").replace("_", " "))
        } catch (t: Throwable) {
            ImportOutcome.Failed("Download failed: ${t.localizedMessage}", t)
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
                        val outcome = importBook(contentUri, name)
                        (outcome as? ImportOutcome.Added)?.let { importedBooks.add(it.book) }
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
                val outcome = importBook(Uri.fromFile(file), file.name)
                (outcome as? ImportOutcome.Added)?.let { importedBooks.add(it.book) }
            } catch (e: Exception) {
                Log.w("BookRepository", "Discovered file import note: ${file.name}", e)
            }
        }

        importedBooks
    }
}
