package com.example.ui.screens.reader

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Bookmark
import com.example.data.model.Highlight
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.data.parser.ParsedBook
import com.example.data.parser.PdfBookParser
import com.example.data.parser.PdfPageText
import com.example.data.repository.BookRepository
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.dictionary.DictionaryLookup
import com.example.domain.tts.TextToSpeechManager
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PdfDisplayMode { PAGE, TEXT }

/**
 * The reader's view of one page, regardless of whether it came from an EPUB spine item
 * or a lazily extracted PDF text layer.
 *
 * [Status.NO_TEXT] is the important addition. PDFs without a text layer -- scans, or
 * documents using font encodings we cannot decode -- previously surfaced as a page whose
 * prose was the literal string "Page 7 of 412", which then flowed into text mode,
 * narration and search. Modelling "there is no text here" explicitly lets the UI say so.
 */
data class ReaderPage(
    val title: String,
    val paragraphs: List<String>,
    val plainText: String,
    val status: Status
) {
    enum class Status { READY, LOADING, NO_TEXT }

    companion object {
        val Loading = ReaderPage("", emptyList(), "", Status.LOADING)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    bookRepository: BookRepository,
    settingsRepository: SettingsRepository,
    sessionRepository: ReadingSessionRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bookFlow by bookRepository.getBookById(bookId).collectAsState(initial = null)
    val settings by settingsRepository.settings.collectAsState()
    val bookmarks by bookRepository.getBookmarksForBook(bookId).collectAsState(initial = emptyList())
    val highlights by bookRepository.getHighlightsForBook(bookId).collectAsState(initial = emptyList())

    var parsedBook by remember { mutableStateOf<ParsedBook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showChrome by remember { mutableStateOf(false) }
    var pdfDisplayMode by remember { mutableStateOf(PdfDisplayMode.PAGE) }
    var activeCursorSelection by remember { mutableStateOf<CursorSelectionState?>(null) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var activeNoteInput by remember { mutableStateOf("") }
    var wordLookupResult by remember { mutableStateOf<com.example.domain.dictionary.WordDefinition?>(null) }

    val ttsManager = remember { TextToSpeechManager(context) }
    val isTtsPlaying by ttsManager.isPlaying.collectAsState()
    val currentTtsSentence by ttsManager.currentSentenceIndex.collectAsState()
    val sentencesFlow by ttsManager.sentencesFlow.collectAsState()
    val sleepTimerMins by ttsManager.sleepTimerMinutesLeft.collectAsState()

    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val book = bookFlow
    val currentTheme = settings.theme
    val bgColor = Color(currentTheme.bgHex)
    val textColor = Color(currentTheme.textHex)
    val isPdf = book?.format == "PDF"

    var loadedBookId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bookId, book?.id) {
        val b = book ?: return@LaunchedEffect
        if (loadedBookId != bookId) {
            isLoading = true
            loadedBookId = bookId
            val content = withContext(Dispatchers.IO) { bookRepository.loadBookContent(b) }
            parsedBook = content
            isLoading = false
        }
    }

    // A PDF session is opened once per file and reused for every page render and text
    // lookup, so swiping never re-opens a file descriptor.
    var pdfRenderer by remember { mutableStateOf<PdfBookParser.PdfDocumentRenderer?>(null) }
    var rendererFailed by remember { mutableStateOf(false) }

    LaunchedEffect(book?.filePath, isPdf) {
        val path = book?.filePath
        if (isPdf && path != null) {
            rendererFailed = false
            val opened = withContext(Dispatchers.IO) {
                PdfBookParser.createRenderer(context, Uri.parse(path))
            }
            pdfRenderer?.close()
            pdfRenderer = opened
            rendererFailed = opened == null
        } else {
            pdfRenderer?.close()
            pdfRenderer = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            ttsManager.release()
        }
    }

    val totalChapters = (
        if (isPdf && (pdfRenderer?.pageCount ?: 0) > 0) pdfRenderer!!.pageCount
        else parsedBook?.chapters?.size ?: parsedBook?.totalPagesEstimate ?: 1
        ).coerceAtLeast(1)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var jumpToPageInput by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { totalChapters })

    var hasRestoredLocation by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(totalChapters, book?.id) {
        if (!hasRestoredLocation && book != null) {
            val target = book.progressLocation.toIntOrNull() ?: 0
            if (target in 0 until totalChapters) pagerState.scrollToPage(target)
            hasRestoredLocation = true
        }
    }

    // A reading session spans the whole visit. Recording how far the reader actually
    // travelled requires remembering where they started.
    DisposableEffect(bookId) {
        sessionRepository.startSession(bookId, pagerState.currentPage)
        onDispose {
            // Deliberately not `scope.launch`: `rememberCoroutineScope` is cancelled the
            // moment this composable leaves the tree, so the previous code's session
            // write raced with its own cancellation and was usually dropped. The
            // repository owns a scope that outlives the screen.
            sessionRepository.stopSession(pagerState.currentPage)
        }
    }

    // Progress is debounced: a fast flick through twenty pages used to issue twenty
    // separate database writes on the main-thread-adjacent path.
    LaunchedEffect(book?.id, totalChapters, isLoading) {
        val b = book ?: return@LaunchedEffect
        if (isLoading) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .debounce(400)
            .collect { index ->
                val percent = ((index + 1).toFloat() / totalChapters.toFloat()) * 100f
                bookRepository.updateProgress(
                    bookId = b.id,
                    percent = percent,
                    location = index.toString(),
                    chapterTitle = if (isPdf) "Page ${index + 1}"
                    else parsedBook?.chapters?.getOrNull(index)?.title ?: "",
                    status = if (percent >= 99f) "FINISHED" else "READING"
                )
            }
    }

    // -------- page content: chapters for EPUB/TXT, lazily extracted text for PDF -----

    val pdfPageText = remember { mutableStateMapOf<Int, PdfPageText>() }

    LaunchedEffect(pdfRenderer, pagerState.currentPage, pdfDisplayMode) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        // Fetch the visible page first, then its neighbours, so swiping into an adjacent
        // page finds text already waiting.
        val wanted = listOf(
            pagerState.currentPage,
            pagerState.currentPage + 1,
            pagerState.currentPage - 1
        ).filter { it in 0 until renderer.pageCount && it !in pdfPageText }

        for (index in wanted) {
            val extracted = withContext(Dispatchers.IO) { renderer.pageText(index) }
            pdfPageText[index] = extracted
        }
    }

    fun pageAt(index: Int): ReaderPage {
        if (!isPdf) {
            val chapter = parsedBook?.chapters?.getOrNull(index)
                ?: return ReaderPage("Page ${index + 1}", emptyList(), "", ReaderPage.Status.NO_TEXT)
            return ReaderPage(
                title = chapter.title,
                paragraphs = chapter.formattedParagraphs,
                plainText = chapter.plainText,
                status = ReaderPage.Status.READY
            )
        }
        // A PDF may still arrive with pre-parsed chapters (the bundled samples do).
        // Prefer those over re-extracting text we already have.
        parsedBook?.chapters?.getOrNull(index)?.takeIf { it.plainText.isNotBlank() }?.let { chapter ->
            return ReaderPage(chapter.title, chapter.formattedParagraphs, chapter.plainText, ReaderPage.Status.READY)
        }
        val extracted = pdfPageText[index] ?: return ReaderPage.Loading.copy(title = "Page ${index + 1}")
        return if (extracted.hasText) {
            ReaderPage(
                title = "Page ${index + 1}",
                paragraphs = extracted.paragraphs,
                plainText = extracted.text.orEmpty(),
                status = ReaderPage.Status.READY
            )
        } else {
            ReaderPage("Page ${index + 1}", emptyList(), "", ReaderPage.Status.NO_TEXT)
        }
    }

    val currentPage = pageAt(pagerState.currentPage)
    val currentPageTitle = currentPage.title
    val pageParagraphs = currentPage.paragraphs

    // Highlights for the page currently on screen, projected onto paragraph offsets.
    fun highlightsFor(pageIndex: Int, paragraphIndex: Int, paragraph: String): List<ParagraphHighlight> =
        highlights.mapNotNull { saved ->
            if (saved.pageIndex != pageIndex) return@mapNotNull null
            val color = runCatching { Color(android.graphics.Color.parseColor(saved.colorHex)) }
                .getOrDefault(AmberGold)

            when {
                // Preferred path: exact character range recorded at selection time.
                saved.paragraphIndex == paragraphIndex && saved.startOffset >= 0 && saved.endOffset > saved.startOffset ->
                    ParagraphHighlight(saved.startOffset, saved.endOffset, color)

                // Legacy rows (and EPUB re-flows) have only the quoted text. Locate it.
                saved.selectedText.isNotBlank() -> {
                    val at = paragraph.indexOf(saved.selectedText)
                    if (at >= 0) ParagraphHighlight(at, at + saved.selectedText.length, color) else null
                }

                else -> null
            }
        }

    // Narration always reads the page the reader is actually looking at.
    LaunchedEffect(pagerState.currentPage, currentPage.plainText) {
        if (currentPage.plainText.isNotBlank()) ttsManager.setContent(currentPage.plainText)
    }

    LaunchedEffect(pagerState, totalChapters) {
        ttsManager.onPageFinishedListener = {
            if (pagerState.currentPage < totalChapters - 1) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    val next = pageAt(pagerState.currentPage)
                    if (next.plainText.isNotBlank()) {
                        ttsManager.setContent(next.plainText)
                        ttsManager.play()
                    }
                }
            }
        }
    }

    val resolvedFontFamily = when (settings.fontFamily) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS -> FontFamily.SansSerif
        ReaderFont.DYSLEXIC -> FontFamily.Default
        ReaderFont.MONO -> FontFamily.Monospace
    }

    val isCurrentPageBookmarked = remember(bookmarks, pagerState.currentPage) {
        bookmarks.any { it.pageIndex == pagerState.currentPage }
    }

    if (isLoading || book == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AmberGold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Opening book...", color = textColor)
            }
        }
        return
    }

    Box(
        modifier = modifier.fillMaxSize().background(bgColor).testTag("reader_root")
    ) {
        val onPrev: () -> Unit = {
            if (pagerState.currentPage > 0) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        }
        val onNext: () -> Unit = {
            if (pagerState.currentPage < totalChapters - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }

        if (isPdf && pdfDisplayMode == PdfDisplayMode.TEXT) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = (if (currentTheme == ReaderTheme.OLED) Color(0xFF1E293B)
                    else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article, contentDescription = null,
                                tint = AmberGold, modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Selectable text", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = AmberGold
                            )
                        }
                        TextButton(
                            onClick = { pdfDisplayMode = PdfDisplayMode.PAGE },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View page", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                    TextPageView(
                        page = pageAt(pageIndex),
                        settings = settings,
                        textColor = textColor,
                        fontFamily = resolvedFontFamily,
                        onCenterTap = { showChrome = !showChrome },
                        onPrevPage = onPrev,
                        onNextPage = onNext,
                        activeSelection = activeCursorSelection,
                        onSelectionChange = { activeCursorSelection = it },
                        highlightsFor = { paraIndex, paraText -> highlightsFor(pageIndex, paraIndex, paraText) },
                        isTtsActive = isTtsPlaying,
                        activeTtsSentence = currentTtsSentence,
                        onViewPageImage = { pdfDisplayMode = PdfDisplayMode.PAGE }
                    )
                }
            }
        } else if (isPdf) {
            if (settings.readingMode == ReadingMode.PAGINATED) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("pdf_horizontal_pager")
                ) { pageIndex ->
                    PdfSinglePageView(
                        pageIndex = pageIndex,
                        pdfRenderer = pdfRenderer,
                        rendererFailed = rendererFailed,
                        theme = currentTheme,
                        onCenterTap = { showChrome = !showChrome },
                        onPrevPage = onPrev,
                        onNextPage = onNext,
                        onOpenTextMode = { pdfDisplayMode = PdfDisplayMode.TEXT },
                        onStartTts = {
                            val page = pageAt(pageIndex)
                            if (page.plainText.isNotBlank()) {
                                ttsManager.setContent(page.plainText)
                                ttsManager.play()
                            }
                            showTtsSheet = true
                        },
                        hasTextLayer = pageAt(pageIndex).status == ReaderPage.Status.READY
                    )
                }
            } else {
                PdfContinuousScrollView(
                    pdfRenderer = pdfRenderer,
                    totalPages = totalChapters,
                    initialPage = pagerState.currentPage,
                    theme = currentTheme,
                    onPageVisible = { pageIdx ->
                        if (pagerState.currentPage != pageIdx) {
                            scope.launch { pagerState.scrollToPage(pageIdx) }
                        }
                    },
                    onCenterTap = { showChrome = !showChrome }
                )
            }
        } else {
            if (settings.readingMode == ReadingMode.PAGINATED) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                    TextPageView(
                        page = pageAt(pageIndex),
                        settings = settings,
                        textColor = textColor,
                        fontFamily = resolvedFontFamily,
                        onCenterTap = { showChrome = !showChrome },
                        onPrevPage = onPrev,
                        onNextPage = onNext,
                        activeSelection = activeCursorSelection,
                        onSelectionChange = { activeCursorSelection = it },
                        highlightsFor = { paraIndex, paraText -> highlightsFor(pageIndex, paraIndex, paraText) },
                        isTtsActive = isTtsPlaying,
                        activeTtsSentence = currentTtsSentence
                    )
                }
            } else {
                ContinuousScrollView(
                    parsedBook = parsedBook,
                    settings = settings,
                    textColor = textColor,
                    fontFamily = resolvedFontFamily,
                    onCenterTap = { showChrome = !showChrome },
                    activeSelection = activeCursorSelection,
                    onSelectionChange = { activeCursorSelection = it },
                    highlightsFor = { chapterIndex, paraIndex, paraText ->
                        highlightsFor(chapterIndex, paraIndex, paraText)
                    },
                    isTtsActive = isTtsPlaying,
                    activeTtsSentence = currentTtsSentence
                )
            }
        }

        if (settings.nightLightWarmth > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFB300).copy(alpha = settings.nightLightWarmth * 0.35f))
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = (if (currentTheme == ReaderTheme.OLED) Color.Black
                else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentPageTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isCurrentPageBookmarked) {
                                    bookmarks.find { it.pageIndex == pagerState.currentPage }
                                        ?.let { bookRepository.removeBookmark(it.id) }
                                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                } else {
                                    bookRepository.addBookmark(
                                        Bookmark(
                                            bookId = book.id,
                                            location = "page:${pagerState.currentPage}",
                                            pageIndex = pagerState.currentPage,
                                            chapterTitle = currentPageTitle,
                                            snippetText = pageParagraphs.firstOrNull()?.take(80) ?: ""
                                        )
                                    )
                                    Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("bookmark_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            contentDescription = "Toggle bookmark",
                            tint = if (isCurrentPageBookmarked) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("toc_button")) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of contents")
                    }

                    IconButton(onClick = { showSearchSheet = true }, modifier = Modifier.testTag("in_book_search_button")) {
                        Icon(Icons.Default.Search, contentDescription = "Search book")
                    }

                    if (isPdf) {
                        IconButton(
                            onClick = {
                                pdfDisplayMode = if (pdfDisplayMode == PdfDisplayMode.PAGE) {
                                    PdfDisplayMode.TEXT
                                } else {
                                    PdfDisplayMode.PAGE
                                }
                            },
                            modifier = Modifier.testTag("pdf_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (pdfDisplayMode == PdfDisplayMode.PAGE) {
                                    Icons.AutoMirrored.Filled.Article
                                } else {
                                    Icons.Default.Description
                                },
                                contentDescription = if (pdfDisplayMode == PdfDisplayMode.PAGE) {
                                    "Switch to selectable text"
                                } else {
                                    "Switch to page image"
                                },
                                tint = if (pdfDisplayMode == PdfDisplayMode.TEXT) AmberGold
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = { showTtsSheet = true }, modifier = Modifier.testTag("tts_button")) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Audio narrator",
                            tint = if (isTtsPlaying) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showAppearanceSheet = true }, modifier = Modifier.testTag("appearance_button")) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font and display settings")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = (if (currentTheme == ReaderTheme.OLED) Color.Black
                else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    val displayPage = (if (isDraggingSlider) sliderDragValue.toInt() else pagerState.currentPage)
                        .coerceIn(0, totalChapters - 1)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                jumpToPageInput = (displayPage + 1).toString()
                                showJumpToPageDialog = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPdf) "Page ${displayPage + 1} of $totalChapters (tap to jump)"
                            else "$currentPageTitle (${displayPage + 1} of $totalChapters)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "${(((displayPage + 1).toFloat() / totalChapters.toFloat()) * 100f).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPrev,
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous page",
                                tint = if (pagerState.currentPage > 0) AmberGold else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Slider(
                            value = if (isDraggingSlider) sliderDragValue else pagerState.currentPage.toFloat(),
                            onValueChange = {
                                isDraggingSlider = true
                                sliderDragValue = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                scope.launch {
                                    pagerState.scrollToPage(sliderDragValue.toInt().coerceIn(0, totalChapters - 1))
                                }
                            },
                            valueRange = 0f..(totalChapters - 1).toFloat().coerceAtLeast(0f),
                            colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold),
                            modifier = Modifier.weight(1f).testTag("progress_scrubber_slider")
                        )

                        IconButton(
                            onClick = onNext,
                            enabled = pagerState.currentPage < totalChapters - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next page",
                                tint = if (pagerState.currentPage < totalChapters - 1) AmberGold
                                else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    val minutesLeft = ((totalChapters - pagerState.currentPage) * 300 / 220).coerceAtLeast(1)
                    Text(
                        text = "$minutesLeft min left in book",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = activeCursorSelection != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showChrome) 125.dp else 24.dp)
        ) {
            activeCursorSelection?.let { sel ->
                CursorSelectionFloatingBar(
                    selection = sel,
                    paragraphText = pageParagraphs.getOrNull(sel.paragraphIndex) ?: sel.selectedText,
                    onSelectionChange = { activeCursorSelection = it },
                    onSpeak = { ttsManager.speakText(it) },
                    onDefine = { wordLookupResult = DictionaryLookup.getDefinition(it) },
                    onHighlight = { colorHex ->
                        scope.launch {
                            bookRepository.addHighlight(
                                Highlight(
                                    bookId = bookId,
                                    location = "p:${pagerState.currentPage}:para:${sel.paragraphIndex}",
                                    pageIndex = pagerState.currentPage,
                                    paragraphIndex = sel.paragraphIndex,
                                    startOffset = sel.start,
                                    endOffset = sel.end,
                                    colorHex = colorHex,
                                    selectedText = sel.selectedText,
                                    chapterTitle = currentPageTitle
                                )
                            )
                            Toast.makeText(context, "Highlight saved", Toast.LENGTH_SHORT).show()
                            activeCursorSelection = null
                        }
                    },
                    onAddNote = {
                        activeNoteInput = ""
                        showNoteDialog = true
                    },
                    onShare = {
                        DictionaryLookup.shareTextOrQuote(context, sel.selectedText, book.title, book.author)
                        activeCursorSelection = null
                    },
                    onClear = { activeCursorSelection = null }
                )
            }
        }
    }

    wordLookupResult?.let { def ->
        AlertDialog(
            onDismissRequest = { wordLookupResult = null },
            title = { Text(def.word.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(def.partOfSpeech, fontSize = 12.sp, color = AmberGold, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(def.definition, style = MaterialTheme.typography.bodyMedium)
                    def.example?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Example: \u201C$it\u201D",
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row {
                        Button(
                            onClick = {
                                DictionaryLookup.openWikipedia(context, def.word)
                                wordLookupResult = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wikipedia", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                DictionaryLookup.openWebSearch(context, def.word)
                                wordLookupResult = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Web search") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { wordLookupResult = null }) { Text("Close") } }
        )
    }

    if (showJumpToPageDialog) {
        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Jump to page") },
            text = {
                Column {
                    Text(
                        "Enter a page number between 1 and $totalChapters:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jumpToPageInput,
                        onValueChange = { input -> jumpToPageInput = input.filter { it.isDigit() } },
                        label = { Text("Page number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entered = jumpToPageInput.toIntOrNull()
                        if (entered != null && entered in 1..totalChapters) {
                            scope.launch { pagerState.scrollToPage(entered - 1) }
                            showJumpToPageDialog = false
                        } else {
                            Toast.makeText(
                                context,
                                "Please enter a valid page (1 to $totalChapters)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) { Text("Jump", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showJumpToPageDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNoteDialog) {
        val selection = activeCursorSelection
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Attach a note") },
            text = {
                Column {
                    if (selection != null) {
                        Text(
                            "\u201C${selection.selectedText.take(140)}\u201D",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = activeNoteInput,
                        onValueChange = { activeNoteInput = it },
                        placeholder = { Text("Write your reflections, thoughts, or vocabulary notes...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = selection != null,
                    onClick = {
                        // The note used to be saved against `selectedParagraphText`, a
                        // variable that was never assigned -- so every note was attached
                        // to an empty quotation.
                        val sel = selection ?: return@Button
                        scope.launch {
                            bookRepository.addHighlight(
                                Highlight(
                                    bookId = bookId,
                                    location = "p:${pagerState.currentPage}:para:${sel.paragraphIndex}",
                                    pageIndex = pagerState.currentPage,
                                    paragraphIndex = sel.paragraphIndex,
                                    startOffset = sel.start,
                                    endOffset = sel.end,
                                    colorHex = "#FFE082",
                                    selectedText = sel.selectedText,
                                    note = activeNoteInput,
                                    chapterTitle = currentPageTitle
                                )
                            )
                            Toast.makeText(context, "Note attached", Toast.LENGTH_SHORT).show()
                            showNoteDialog = false
                            activeCursorSelection = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) { Text("Save note", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AppearanceSettingsContent(
                settings = settings,
                onSettingsChange = { settingsRepository.updateSettings(it) }
            )
        }
    }

    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TableOfContentsContent(
                parsedBook = parsedBook,
                currentPage = pagerState.currentPage,
                bookmarks = bookmarks,
                highlights = highlights,
                onSelectChapter = { index ->
                    scope.launch {
                        pagerState.scrollToPage(index.coerceIn(0, totalChapters - 1))
                        showTocSheet = false
                    }
                },
                onDeleteBookmark = { id -> scope.launch { bookRepository.removeBookmark(id) } },
                onDeleteHighlight = { id -> scope.launch { bookRepository.removeHighlight(id) } }
            )
        }
    }

    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            InBookSearchContent(
                parsedBook = parsedBook,
                pdfRenderer = pdfRenderer,
                totalPages = totalChapters,
                onSelectMatch = { index ->
                    scope.launch {
                        pagerState.scrollToPage(index.coerceIn(0, totalChapters - 1))
                        showSearchSheet = false
                    }
                }
            )
        }
    }

    if (showTtsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTtsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            TtsAudioControlsContent(
                pageTitle = if (isPdf) "Page ${pagerState.currentPage + 1} of $totalChapters" else currentPageTitle,
                isPlaying = isTtsPlaying,
                currentSentence = currentTtsSentence,
                sentences = sentencesFlow,
                sleepTimerMins = sleepTimerMins,
                speed = settings.ttsSpeed,
                onPlayPause = {
                    if (isTtsPlaying) {
                        ttsManager.pause()
                    } else {
                        if (currentPage.plainText.isNotBlank()) ttsManager.setContent(currentPage.plainText)
                        ttsManager.play()
                    }
                },
                onPrev = { ttsManager.previousSentence() },
                onNext = { ttsManager.nextSentence() },
                onSelectSentence = { ttsManager.jumpToSentence(it) },
                onSpeedChange = { newSpeed ->
                    ttsManager.setSpeed(newSpeed)
                    settingsRepository.updateSettings(settings.copy(ttsSpeed = newSpeed))
                },
                onSetSleepTimer = { ttsManager.setSleepTimer(it) },
                onCancelSleepTimer = { ttsManager.cancelSleepTimer() }
            )
        }
    }
}
