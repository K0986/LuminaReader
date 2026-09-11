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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.Bookmark
import com.example.data.model.Highlight
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.data.parser.ParsedBook
import com.example.data.parser.PdfBookParser
import com.example.data.parser.pdf.PdfSelection
import com.example.data.parser.pdf.PdfTextCapability
import com.example.data.parser.pdf.PdfTextEngine
import com.example.data.parser.pdf.PdfTextEngines
import com.example.data.repository.BookRepository
import com.example.data.repository.PdfIndexProgress
import com.example.data.repository.PdfTextIndexer
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SearchSnippets
import com.example.data.repository.SettingsRepository
import com.example.domain.dictionary.DictionaryLookup
import com.example.domain.tts.TextToSpeechManager
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PdfDisplayMode {
    PAGE,
    TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    bookRepository: BookRepository,
    settingsRepository: SettingsRepository,
    sessionRepository: ReadingSessionRepository,
    pdfTextIndexer: PdfTextIndexer,
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

    // Chrome visibility (top & bottom navigation bars)
    var showChrome by remember { mutableStateOf(false) }

    // PDF view mode: the rendered page, or its reflowed text
    var pdfDisplayMode by remember { mutableStateOf(PdfDisplayMode.PAGE) }

    // Selections: reflowed text keeps a character range, the PDF page keeps a platform selection
    var textSelection by remember { mutableStateOf<TextSelection?>(null) }
    var pdfSelection by remember { mutableStateOf<PdfSelection?>(null) }
    var isPageZoomed by remember { mutableStateOf(false) }

    // Sheets & dialogs
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
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // In-reader brightness. The setting already existed but was never applied to the window, so
    // "Screen Brightness" did nothing; -1 means "follow the system".
    DisposableEffect(settings.brightness) {
        val window = (context as? Activity)?.window
        window?.let {
            it.attributes = it.attributes.apply {
                screenBrightness = if (settings.brightness < 0f) {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    settings.brightness.coerceIn(0.05f, 1f)
                }
            }
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    DisposableEffect(bookId) {
        sessionRepository.startSession(bookId)
        onDispose {
            ttsManager.release()
            scope.launch { sessionRepository.stopSession(1) }
        }
    }

    val book = bookFlow
    val currentTheme = settings.theme
    val bgColor = Color(currentTheme.bgHex)
    val textColor = Color(currentTheme.textHex)
    val isPdf = book?.format == "PDF"

    // Load book content once per bookId to prevent an infinite update loop
    var loadedBookId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bookId, book?.id) {
        val current = book ?: return@LaunchedEffect
        if (loadedBookId != bookId) {
            isLoading = true
            loadedBookId = bookId
            val content = withContext(Dispatchers.IO) { bookRepository.loadBookContent(current) }
            parsedBook = content
            isLoading = false
        }
    }

    // Page rendering session
    var pdfRenderer by remember { mutableStateOf<PdfBookParser.PdfDocumentRenderer?>(null) }
    // Text layer session (words, glyph boxes, search) — independent of rendering
    var pdfTextEngine by remember { mutableStateOf<PdfTextEngine?>(null) }

    LaunchedEffect(book?.filePath, isPdf) {
        val path = book?.filePath
        if (isPdf && path != null) {
            val uri = Uri.parse(path)
            val renderer = withContext(Dispatchers.IO) { PdfBookParser.createRenderer(context, uri) }
            pdfRenderer?.close()
            pdfRenderer = renderer
            val engine = withContext(Dispatchers.IO) {
                PdfTextEngines.open(context, uri, renderer?.pageCount ?: 0)
            }
            pdfTextEngine?.close()
            pdfTextEngine = engine
        } else {
            pdfRenderer?.close()
            pdfRenderer = null
            pdfTextEngine?.close()
            pdfTextEngine = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            pdfTextEngine?.close()
        }
    }

    val totalChapters = (
        if (isPdf) {
            pdfRenderer?.pageCount?.takeIf { it > 0 } ?: book?.totalPages ?: 1
        } else {
            parsedBook?.chapters?.size ?: 1
        }
        ).coerceAtLeast(1)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var jumpToPageInput by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalChapters }
    )

    // Restore the reading location once per book
    var hasRestoredLocation by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(totalChapters, book?.id) {
        if (!hasRestoredLocation && totalChapters > 1 && book != null) {
            val target = book.progressLocation.toIntOrNull() ?: 0
            if (target in 0 until totalChapters && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
            hasRestoredLocation = true
        }
    }

    // ---- "Process first, then open": one-time text extraction for PDFs -------------------------
    var indexProgress by remember(bookId) { mutableStateOf<PdfIndexProgress?>(null) }
    var openedWithoutIndex by remember(bookId) { mutableStateOf(false) }

    LaunchedEffect(bookId, isPdf, totalChapters, book?.filePath) {
        val current = book ?: return@LaunchedEffect
        if (!isPdf || totalChapters <= 0) return@LaunchedEffect
        if (pdfTextIndexer.isIndexed(bookId, totalChapters)) {
            indexProgress = PdfIndexProgress.Complete(
                pagesWithText = pdfTextIndexer.pagesWithText(bookId),
                total = totalChapters,
                capability = pdfTextEngine?.capability ?: PdfTextCapability.TEXT_ONLY
            )
            return@LaunchedEffect
        }
        pdfTextIndexer.index(current, totalChapters).collect { progress ->
            indexProgress = progress
        }
    }

    // Current page text for PDFs comes from the index, not from a re-parse on every swipe.
    var pdfPageText by remember(bookId) { mutableStateOf("") }
    LaunchedEffect(bookId, isPdf, pagerState.currentPage, indexProgress) {
        if (!isPdf) return@LaunchedEffect
        pdfPageText = pdfTextIndexer.pageText(bookId, pagerState.currentPage).orEmpty()
    }

    // Saved highlights, re-resolved to rectangles so they can be painted onto the page image.
    var pdfHighlightOverlays by remember(bookId) { mutableStateOf<List<PdfHighlightOverlay>>(emptyList()) }
    LaunchedEffect(pagerState.currentPage, highlights, pdfTextEngine) {
        val engine = pdfTextEngine
        if (!isPdf || engine == null || engine.capability != PdfTextCapability.TEXT_AND_SELECTION) {
            pdfHighlightOverlays = emptyList()
            return@LaunchedEffect
        }
        val page = pagerState.currentPage
        pdfHighlightOverlays = withContext(Dispatchers.IO) {
            highlights.mapNotNull { highlight ->
                val decoded = PdfHighlightLocation.decode(highlight.location) ?: return@mapNotNull null
                if (decoded.pageIndex != page) return@mapNotNull null
                val resolved = engine.selectRange(page, decoded.startChar, decoded.endChar)
                    ?: return@mapNotNull null
                PdfHighlightOverlay(
                    rects = resolved.rects,
                    color = parseHighlightColor(highlight.colorHex)
                )
            }
        }
    }

    // Clearing a stale selection when the page changes avoids acting on text you can no longer see.
    LaunchedEffect(pagerState.currentPage) {
        textSelection = null
        pdfSelection = null
    }

    // Auto-update reading progress when the page changes
    LaunchedEffect(pagerState.currentPage, totalChapters) {
        if (!isLoading && book != null && totalChapters > 0) {
            val currentIdx = pagerState.currentPage
            val percent = ((currentIdx + 1).toFloat() / totalChapters.toFloat()) * 100f
            val chapterTitle = if (isPdf) {
                "Page ${currentIdx + 1}"
            } else {
                parsedBook?.chapters?.getOrNull(currentIdx)?.title ?: ""
            }
            bookRepository.updateProgress(
                bookId = book.id,
                percent = percent,
                location = currentIdx.toString(),
                chapterTitle = chapterTitle,
                status = if (percent >= 99f) "FINISHED" else "READING"
            )
        }
    }

    val isCurrentPageBookmarked = remember(bookmarks, pagerState.currentPage) {
        bookmarks.any { it.pageIndex == pagerState.currentPage }
    }

    val currentChapter = parsedBook?.chapters?.getOrNull(pagerState.currentPage)
    val currentPageTitle = if (isPdf) "Page ${pagerState.currentPage + 1}" else currentChapter?.title.orEmpty()
    val currentPageText = if (isPdf) pdfPageText else currentChapter?.plainText.orEmpty()
    val currentParagraphs = remember(currentPageText, isPdf, currentChapter) {
        if (isPdf) {
            TextSelectionMath.toParagraphs(pdfPageText)
        } else {
            currentChapter?.formattedParagraphs ?: emptyList()
        }
    }

    LaunchedEffect(currentPageText) {
        if (currentPageText.isNotBlank()) {
            ttsManager.setContent(currentPageText)
        }
    }

    // Narration rolls onto the next page when the current one finishes
    LaunchedEffect(pagerState, totalChapters, isPdf) {
        ttsManager.onPageFinishedListener = {
            if (pagerState.currentPage < totalChapters - 1) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
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

    if (isLoading || book == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AmberGold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading book...", color = textColor)
            }
        }
        return
    }

    val preparation = indexProgress
    val needsPreparation = isPdf && !openedWithoutIndex && preparation !is PdfIndexProgress.Complete
    if (needsPreparation) {
        DocumentPreparingView(
            title = book.title,
            done = (preparation as? PdfIndexProgress.Working)?.done ?: 0,
            total = (preparation as? PdfIndexProgress.Working)?.total ?: totalChapters,
            errorMessage = (preparation as? PdfIndexProgress.Failed)?.message,
            backgroundColor = bgColor,
            textColor = textColor,
            onReadNow = { openedWithoutIndex = true },
            onBack = onBack,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("reader_root")
    ) {
        if (isPdf && pdfDisplayMode == PdfDisplayMode.TEXT) {
            Column(modifier = Modifier.fillMaxSize()) {
                PdfTextModeBanner(
                    theme = currentTheme,
                    onShowPage = { pdfDisplayMode = PdfDisplayMode.PAGE }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val paragraphs = if (pageIndex == pagerState.currentPage) {
                        currentParagraphs
                    } else {
                        emptyList()
                    }
                    ReflowPageView(
                        title = "Page ${pageIndex + 1}",
                        paragraphs = paragraphs,
                        emptyMessage = "No selectable text was found on this page. It is most likely a scan or an image.",
                        settings = settings,
                        textColor = textColor,
                        fontFamily = resolvedFontFamily,
                        onCenterTap = { showChrome = !showChrome },
                        onPrevPage = {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        onNextPage = {
                            if (pagerState.currentPage < totalChapters - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        activeSelection = textSelection,
                        onSelectionChange = { textSelection = it },
                        highlights = highlights.filter { it.pageIndex == pageIndex },
                        isTtsActive = isTtsPlaying,
                        activeTtsSentence = currentTtsSentence
                    )
                }
            }
        } else if (isPdf) {
            if (settings.readingMode == ReadingMode.PAGINATED) {
                HorizontalPager(
                    state = pagerState,
                    // A zoomed page owns horizontal drags, otherwise panning flips the page instead.
                    userScrollEnabled = !isPageZoomed,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf_horizontal_pager")
                ) { pageIndex ->
                    PdfSinglePageView(
                        pageIndex = pageIndex,
                        pdfRenderer = pdfRenderer,
                        textEngine = pdfTextEngine,
                        theme = currentTheme,
                        selection = pdfSelection?.takeIf { it.pageIndex == pageIndex },
                        onSelectionChange = { pdfSelection = it },
                        highlights = if (pageIndex == pagerState.currentPage) pdfHighlightOverlays else emptyList(),
                        onZoomChange = { zoomed ->
                            if (pageIndex == pagerState.currentPage) isPageZoomed = zoomed
                        },
                        onCenterTap = { showChrome = !showChrome },
                        onPrevPage = {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        onNextPage = {
                            if (pagerState.currentPage < totalChapters - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        onOpenTextMode = { pdfDisplayMode = PdfDisplayMode.TEXT },
                        onStartTts = {
                            if (currentPageText.isNotBlank()) {
                                ttsManager.setContent(currentPageText)
                                ttsManager.play()
                            }
                            showTtsSheet = true
                        }
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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val chapter = parsedBook?.chapters?.getOrNull(pageIndex)
                    if (chapter != null) {
                        ReflowPageView(
                            title = chapter.title,
                            paragraphs = chapter.formattedParagraphs,
                            emptyMessage = "This chapter has no text.",
                            settings = settings,
                            textColor = textColor,
                            fontFamily = resolvedFontFamily,
                            onCenterTap = { showChrome = !showChrome },
                            onPrevPage = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            },
                            onNextPage = {
                                if (pagerState.currentPage < totalChapters - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                            activeSelection = textSelection,
                            onSelectionChange = { textSelection = it },
                            highlights = highlights.filter { it.pageIndex == pageIndex },
                            isTtsActive = isTtsPlaying,
                            activeTtsSentence = currentTtsSentence
                        )
                    }
                }
            } else {
                ContinuousScrollView(
                    parsedBook = parsedBook,
                    settings = settings,
                    textColor = textColor,
                    fontFamily = resolvedFontFamily,
                    onCenterTap = { showChrome = !showChrome },
                    activeSelection = textSelection,
                    onSelectionChange = { textSelection = it },
                    highlights = highlights,
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

        // Top reading chrome (auto-hiding)
        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = (if (currentTheme == ReaderTheme.OLED) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library")
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentPageTitle.ifBlank { "Page ${pagerState.currentPage + 1}" },
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
                                    bookmarks.find { it.pageIndex == pagerState.currentPage }?.let {
                                        bookRepository.removeBookmark(it.id)
                                    }
                                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                } else {
                                    bookRepository.addBookmark(
                                        Bookmark(
                                            bookId = book.id,
                                            location = "page:${pagerState.currentPage}",
                                            pageIndex = pagerState.currentPage,
                                            chapterTitle = currentPageTitle,
                                            snippetText = currentParagraphs.firstOrNull()?.take(80).orEmpty()
                                        )
                                    )
                                    Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("bookmark_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Toggle Bookmark",
                            tint = if (isCurrentPageBookmarked) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("toc_button")) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Table of Contents")
                    }

                    IconButton(onClick = { showSearchSheet = true }, modifier = Modifier.testTag("in_book_search_button")) {
                        Icon(Icons.Default.Search, contentDescription = "Search Book")
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
                                    "Reflowed text mode"
                                } else {
                                    "Original page mode"
                                },
                                tint = if (pdfDisplayMode == PdfDisplayMode.TEXT) AmberGold else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(onClick = { showTtsSheet = true }, modifier = Modifier.testTag("tts_button")) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Audio Narrator",
                            tint = if (isTtsPlaying) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showAppearanceSheet = true }, modifier = Modifier.testTag("appearance_button")) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font & Display Settings")
                    }
                }
            }
        }

        // Bottom reading chrome (scrubber & stats)
        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = (if (currentTheme == ReaderTheme.OLED) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                jumpToPageInput = ((if (isDraggingSlider) sliderDragValue.toInt() else pagerState.currentPage) + 1).toString()
                                showJumpToPageDialog = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayPage = (if (isDraggingSlider) sliderDragValue.toInt() else pagerState.currentPage)
                            .coerceIn(0, totalChapters - 1)
                        Text(
                            text = if (isPdf) {
                                "Page ${displayPage + 1} of $totalChapters (tap to jump)"
                            } else {
                                "${currentPageTitle.ifBlank { "Chapter" }} (${displayPage + 1} of $totalChapters)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val pct = ((displayPage + 1).toFloat() / totalChapters.toFloat()) * 100f
                        Text(
                            text = "${pct.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous page",
                                tint = if (pagerState.currentPage > 0) AmberGold else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Slider(
                            value = if (isDraggingSlider) sliderDragValue else pagerState.currentPage.toFloat(),
                            onValueChange = { pageVal ->
                                isDraggingSlider = true
                                sliderDragValue = pageVal
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                scope.launch {
                                    pagerState.scrollToPage(sliderDragValue.toInt().coerceIn(0, totalChapters - 1))
                                }
                            },
                            valueRange = 0f..(totalChapters - 1).toFloat().coerceAtLeast(0f),
                            colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("progress_scrubber_slider")
                        )

                        IconButton(
                            onClick = {
                                if (pagerState.currentPage < totalChapters - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                            enabled = pagerState.currentPage < totalChapters - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next page",
                                tint = if (pagerState.currentPage < totalChapters - 1) AmberGold else Color.Gray.copy(alpha = 0.4f),
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

        // Selection toolbar, shared by reflowed text and the PDF page
        val activeSelectionText = textSelection?.text ?: pdfSelection?.text
        AnimatedVisibility(
            visible = activeSelectionText != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showChrome) 125.dp else 24.dp)
        ) {
            if (activeSelectionText != null) {
                val reflow = textSelection
                val paragraphText = reflow?.let { currentParagraphs.getOrNull(it.paragraphIndex) }.orEmpty()

                SelectionActionBar(
                    selectedText = activeSelectionText,
                    wordCount = activeSelectionText.split(Regex("\\s+")).count { it.isNotBlank() },
                    canExtendStart = reflow != null && reflow.range.first > 0,
                    canShrinkStart = reflow != null && reflow.range.first < reflow.range.last,
                    canShrinkEnd = reflow != null && reflow.range.last > reflow.range.first,
                    canExtendEnd = reflow != null && reflow.range.last < paragraphText.length - 1,
                    onNudgeStart = { delta ->
                        textSelection?.let { current ->
                            val range = TextSelectionMath.nudgeStart(paragraphText, current.range, delta)
                            textSelection = current.copy(
                                range = range,
                                text = TextSelectionMath.substring(paragraphText, range)
                            )
                        }
                    },
                    onNudgeEnd = { delta ->
                        textSelection?.let { current ->
                            val range = TextSelectionMath.nudgeEnd(paragraphText, current.range, delta)
                            textSelection = current.copy(
                                range = range,
                                text = TextSelectionMath.substring(paragraphText, range)
                            )
                        }
                    },
                    onSelectSentence = if (reflow != null) {
                        {
                            textSelection?.let { current ->
                                TextSelectionMath.sentenceRangeAt(paragraphText, current.range.first)
                                    ?.let { range ->
                                        textSelection = current.copy(
                                            range = range,
                                            text = TextSelectionMath.substring(paragraphText, range)
                                        )
                                    }
                            }
                        }
                    } else {
                        null
                    },
                    onSelectAll = if (reflow != null && paragraphText.isNotEmpty()) {
                        {
                            textSelection?.let { current ->
                                textSelection = current.copy(
                                    range = 0..(paragraphText.length - 1),
                                    text = paragraphText
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onSpeak = { text ->
                        ttsManager.speakText(text)
                        Toast.makeText(context, "Reading selection aloud", Toast.LENGTH_SHORT).show()
                    },
                    onDefine = { word -> wordLookupResult = DictionaryLookup.getDefinition(word) },
                    onHighlight = { colorHex ->
                        scope.launch {
                            bookRepository.addHighlight(
                                buildHighlight(
                                    bookId = bookId,
                                    pageIndex = pagerState.currentPage,
                                    chapterTitle = currentPageTitle,
                                    colorHex = colorHex,
                                    note = null,
                                    textSelection = textSelection,
                                    pdfSelection = pdfSelection,
                                    selectedText = activeSelectionText
                                )
                            )
                            Toast.makeText(context, "Highlight saved", Toast.LENGTH_SHORT).show()
                            textSelection = null
                            pdfSelection = null
                        }
                    },
                    onAddNote = {
                        activeNoteInput = ""
                        showNoteDialog = true
                    },
                    onShare = {
                        DictionaryLookup.shareTextOrQuote(context, activeSelectionText, book.title, book.author)
                        textSelection = null
                        pdfSelection = null
                    },
                    onClear = {
                        textSelection = null
                        pdfSelection = null
                    }
                )
            }
        }
    }

    // Word definition popup
    wordLookupResult?.let { definition ->
        AlertDialog(
            onDismissRequest = { wordLookupResult = null },
            title = { Text(text = definition.word.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = definition.partOfSpeech, fontSize = 12.sp, color = AmberGold, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = definition.definition, style = MaterialTheme.typography.bodyMedium)
                    definition.example?.let { example ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Example: “$example”", fontStyle = FontStyle.Italic, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row {
                        Button(
                            onClick = {
                                DictionaryLookup.openWikipedia(context, definition.word)
                                wordLookupResult = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wikipedia", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                DictionaryLookup.openWebSearch(context, definition.word)
                                wordLookupResult = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Web Search")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { wordLookupResult = null }) { Text("Close") }
            }
        )
    }

    if (showJumpToPageDialog) {
        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Jump to Page") },
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
                        label = { Text("Page Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val enteredPage = jumpToPageInput.toIntOrNull()
                        if (enteredPage != null && enteredPage in 1..totalChapters) {
                            scope.launch { pagerState.scrollToPage(enteredPage - 1) }
                            showJumpToPageDialog = false
                        } else {
                            Toast.makeText(context, "Please enter a valid page (1 to $totalChapters)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Jump", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpToPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNoteDialog) {
        val noteTarget = textSelection?.text ?: pdfSelection?.text ?: ""
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Attach Note to Highlight") },
            text = {
                Column {
                    Text(
                        text = "“${noteTarget.take(120)}”",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = activeNoteInput,
                        onValueChange = { activeNoteInput = it },
                        placeholder = { Text("Write your reflections, thoughts, or vocabulary notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            bookRepository.addHighlight(
                                buildHighlight(
                                    bookId = bookId,
                                    pageIndex = pagerState.currentPage,
                                    chapterTitle = currentPageTitle,
                                    colorHex = "#FFE082",
                                    note = activeNoteInput,
                                    textSelection = textSelection,
                                    pdfSelection = pdfSelection,
                                    selectedText = noteTarget
                                )
                            )
                            Toast.makeText(context, "Note attached to highlight", Toast.LENGTH_SHORT).show()
                            showNoteDialog = false
                            textSelection = null
                            pdfSelection = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    enabled = noteTarget.isNotBlank()
                ) {
                    Text("Save Note", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            }
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
        val chapters = parsedBook?.chapters
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            InBookSearchContent(
                search = { query ->
                    if (isPdf) {
                        pdfTextIndexer.search(bookId, query).map { hit ->
                            InBookSearchResult(
                                targetIndex = hit.pageIndex,
                                label = "Page ${hit.pageIndex + 1} · ${hit.matchCount} match${if (hit.matchCount == 1) "" else "es"}",
                                snippet = hit.snippet
                            )
                        }
                    } else {
                        withContext(Dispatchers.Default) {
                            chapters.orEmpty().mapIndexedNotNull { index, chapter ->
                                val snippet = SearchSnippets.build(chapter.plainText, query)
                                    ?: return@mapIndexedNotNull null
                                InBookSearchResult(
                                    targetIndex = index,
                                    label = chapter.title,
                                    snippet = snippet
                                )
                            }
                        }
                    }
                },
                onSelectMatch = { targetIndex ->
                    scope.launch {
                        pagerState.scrollToPage(targetIndex.coerceIn(0, totalChapters - 1))
                        showSearchSheet = false
                    }
                },
                emptyHint = if (isPdf && openedWithoutIndex) {
                    "Text is still being prepared for this PDF — results will improve as it finishes."
                } else {
                    null
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
                pageTitle = currentPageTitle.ifBlank { "Page ${pagerState.currentPage + 1}" },
                isPlaying = isTtsPlaying,
                currentSentence = currentTtsSentence,
                sentences = sentencesFlow,
                sleepTimerMins = sleepTimerMins,
                speed = settings.ttsSpeed,
                onPlayPause = {
                    if (isTtsPlaying) {
                        ttsManager.pause()
                    } else {
                        if (currentPageText.isNotBlank()) {
                            ttsManager.setContent(currentPageText)
                        }
                        ttsManager.play()
                    }
                },
                onPrev = { ttsManager.previousSentence() },
                onNext = { ttsManager.nextSentence() },
                onSpeedChange = { newSpeed ->
                    ttsManager.setSpeed(newSpeed)
                    settingsRepository.updateSettings(settings.copy(ttsSpeed = newSpeed))
                },
                onSetSleepTimer = { minutes -> ttsManager.setSleepTimer(minutes) },
                onCancelSleepTimer = { ttsManager.cancelSleepTimer() }
            )
        }
    }
}

/**
 * Builds the highlight row for whichever kind of selection is active.
 *
 * A PDF highlight remembers platform character indices so it can be re-drawn over the page image;
 * a reflowable highlight remembers the paragraph and character range inside it.
 */
private fun buildHighlight(
    bookId: Long,
    pageIndex: Int,
    chapterTitle: String,
    colorHex: String,
    note: String?,
    textSelection: TextSelection?,
    pdfSelection: PdfSelection?,
    selectedText: String
): Highlight {
    val location = when {
        pdfSelection != null -> PdfHighlightLocation.encode(
            pdfSelection.pageIndex,
            pdfSelection.startChar,
            pdfSelection.endChar
        )
        textSelection != null ->
            "p:$pageIndex:para:${textSelection.paragraphIndex}:${textSelection.range.first}-${textSelection.range.last}"
        else -> "p:$pageIndex"
    }
    return Highlight(
        bookId = bookId,
        location = location,
        pageIndex = pageIndex,
        colorHex = colorHex,
        selectedText = selectedText,
        note = note?.takeIf { it.isNotBlank() },
        chapterTitle = chapterTitle
    )
}

@Composable
private fun PdfTextModeBanner(theme: ReaderTheme, onShowPage: () -> Unit) {
    Surface(
        color = (if (theme == ReaderTheme.OLED) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant)
            .copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Article,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Reflowed text · long-press to select",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AmberGold
                )
            }
            TextButton(
                onClick = onShowPage,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Original page", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A page of reflowable text: chapter of an EPUB/TXT, or the extracted text of a PDF page.
 */
@Composable
fun ReflowPageView(
    title: String,
    paragraphs: List<String>,
    emptyMessage: String,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    activeSelection: TextSelection?,
    onSelectionChange: (TextSelection?) -> Unit,
    highlights: List<Highlight>,
    isTtsActive: Boolean,
    activeTtsSentence: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.22f -> onPrevPage()
                            offset.x > width * 0.78f -> onNextPage()
                            else -> onCenterTap()
                        }
                    }
                )
            }
            .padding(horizontal = settings.marginPaddingDp.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                fontFamily = fontFamily,
                fontSize = (settings.fontSizeSp + 6).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = if (settings.textAlignJustify) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            if (paragraphs.isEmpty()) {
                Text(
                    text = emptyMessage,
                    fontFamily = fontFamily,
                    fontSize = settings.fontSizeSp.sp,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                val highlight = highlights.find {
                    it.selectedText == paragraph || it.location.contains(":para:$paragraphIndex:")
                }

                SelectableParagraph(
                    paragraphIndex = paragraphIndex,
                    text = paragraph,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
                    textColor = textColor,
                    fontFamily = fontFamily,
                    textAlign = if (settings.textAlignJustify) TextAlign.Justify else TextAlign.Start,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    highlightColor = highlight?.let { parseHighlightColor(it.colorHex) },
                    isTtsSentenceActive = isTtsActive && paragraphIndex == activeTtsSentence
                )

                highlight?.note?.let { note ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = note,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * Continuous vertical scroll mode for reflowable books.
 */
@Composable
fun ContinuousScrollView(
    parsedBook: ParsedBook?,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    activeSelection: TextSelection?,
    onSelectionChange: (TextSelection?) -> Unit,
    highlights: List<Highlight>,
    isTtsActive: Boolean = false,
    activeTtsSentence: Int = -1
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCenterTap() })
            }
            .padding(horizontal = settings.marginPaddingDp.dp)
    ) {
        val chapters = parsedBook?.chapters ?: emptyList()
        itemsIndexed(chapters) { chapterIndex, chapter ->
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = chapter.title,
                fontFamily = fontFamily,
                fontSize = (settings.fontSizeSp + 6).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )

            chapter.formattedParagraphs.forEachIndexed { paragraphIndex, paragraph ->
                val highlight = highlights.find {
                    it.selectedText == paragraph || it.location.contains("ch:$chapterIndex:para:$paragraphIndex")
                }

                SelectableParagraph(
                    paragraphIndex = paragraphIndex,
                    text = paragraph,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
                    textColor = textColor,
                    fontFamily = fontFamily,
                    textAlign = if (settings.textAlignJustify) TextAlign.Justify else TextAlign.Start,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    highlightColor = highlight?.let { parseHighlightColor(it.colorHex) },
                    isTtsSentenceActive = isTtsActive && paragraphIndex == activeTtsSentence
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
