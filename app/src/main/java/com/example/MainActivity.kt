package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.repository.BookRepository
import com.example.data.repository.ImportOutcome
import com.example.data.repository.PdfTextIndexer
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.LuminaApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.IncomingBookIntent
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sessionRepository: ReadingSessionRepository
    private lateinit var pdfTextIndexer: PdfTextIndexer

    /**
     * Files handed to us by other apps, in arrival order.
     *
     * The activity is `singleTop`, so a second "Open with" or share while Lumina is already running
     * arrives at [onNewIntent] rather than creating a new task — which is why the incoming files
     * live in a flow rather than in a value captured during [onCreate].
     */
    private val incomingFiles = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bookRepository = BookRepository(this)
        settingsRepository = SettingsRepository(this)
        sessionRepository = ReadingSessionRepository(this)
        pdfTextIndexer = PdfTextIndexer(this)

        acceptIntent(intent)

        setContent {
            MyApplicationTheme {
                val pending by incomingFiles.collectAsState()
                var initialBookId by remember { mutableStateOf<Long?>(null) }
                var importMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(pending) {
                    if (pending.isEmpty()) return@LaunchedEffect
                    incomingFiles.value = emptyList()

                    var firstOpenable: Long? = null
                    var added = 0
                    var failures = 0
                    var lastError: String? = null

                    pending.forEach { uri ->
                        persistReadPermission(uri)
                        when (val outcome = bookRepository.importBook(uri)) {
                            is ImportOutcome.Added -> {
                                added++
                                if (firstOpenable == null) firstOpenable = outcome.book.id
                            }
                            is ImportOutcome.AlreadyInLibrary -> {
                                // Opening the copy they already have keeps progress and highlights.
                                if (firstOpenable == null) firstOpenable = outcome.book.id
                            }
                            is ImportOutcome.Failed -> {
                                failures++
                                lastError = outcome.message
                            }
                        }
                    }

                    importMessage = when {
                        failures > 0 && added == 0 -> lastError ?: "That file could not be opened."
                        failures > 0 -> "Imported $added book(s); $failures could not be read."
                        added > 1 -> "Added $added books to your library."
                        else -> null
                    }
                    // Only jump straight into a single shared book; a batch stays in the library.
                    initialBookId = if (pending.size == 1) firstOpenable else null
                }

                LuminaApp(
                    bookRepository = bookRepository,
                    settingsRepository = settingsRepository,
                    sessionRepository = sessionRepository,
                    pdfTextIndexer = pdfTextIndexer,
                    initialOpenBookId = initialBookId,
                    importMessage = importMessage,
                    onImportMessageShown = { importMessage = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        val uris = IncomingBookIntent.bookUris(intent)
        if (uris.isNotEmpty()) {
            incomingFiles.value = uris
        }
    }

    /**
     * Share grants are single-use, so the app asks to keep read access where the provider allows
     * it. Failure is expected and harmless: the file is copied into app storage during import.
     */
    private fun persistReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
