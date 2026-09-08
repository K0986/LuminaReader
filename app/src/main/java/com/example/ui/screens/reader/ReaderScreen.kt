package com.example.ui.screens.reader

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
import com.example.data.repository.BookRepository
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.dictionary.DictionaryLookup
import com.example.domain.tts.TextToSpeechManager
import com.example.ui.theme.AmberGold
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightRose
import com.example.ui.theme.HighlightSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PdfDisplayMode {
    PAGE,
    TEXT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Chrome visibility (top & bottom navigation bars)
    var showChrome by remember { mutableStateOf(false) }

    // PDF View Mode (Visual Page vs Reflowable Text & Cursor Selection)
    var pdfDisplayMode by remember { mutableStateOf(PdfDisplayMode.PAGE) }

    // Cursor-based text selection state
    var activeCursorSelection by remember { mutableStateOf<CursorSelectionState?>(null) }

    // Sheets & Dialogs
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var selectedParagraphText by remember { mutableStateOf<String?>(null) }
    var activeNoteInput by remember { mutableStateOf("") }
    var wordLookupResult by remember { mutableStateOf<com.example.domain.dictionary.WordDefinition?>(null) }

    // TTS Manager
    val ttsManager = remember { TextToSpeechManager(context) }
    val isTtsPlaying by ttsManager.isPlaying.collectAsState()
    val currentTtsSentence by ttsManager.currentSentenceIndex.collectAsState()
    val sentencesFlow by ttsManager.sentencesFlow.collectAsState()
    val sleepTimerMins by ttsManager.sleepTimerMinutesLeft.collectAsState()

    // Keep screen on
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Session tracking
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

    // Load book content once per bookId to prevent infinite update loop
    var loadedBookId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(bookId, book?.id) {
        val b = book ?: return@LaunchedEffect
        if (loadedBookId != bookId) {
            isLoading = true
            loadedBookId = bookId
            withContext(Dispatchers.IO) {
                val content = bookRepository.loadBookContent(b)
                withContext(Dispatchers.Main) {
                    parsedBook = content
                    isLoading = false
                }
            }
        }
    }

    // PDF document renderer session loaded safely on IO dispatcher
    var pdfRenderer by remember { mutableStateOf<PdfBookParser.PdfDocumentRenderer?>(null) }
    var isRendererLoading by remember { mutableStateOf(false) }

    LaunchedEffect(book?.filePath, isPdf) {
        if (isPdf && book != null) {
            isRendererLoading = true
            withContext(Dispatchers.IO) {
                val r = PdfBookParser.createRenderer(context, Uri.parse(book.filePath))
                withContext(Dispatchers.Main) {
                    pdfRenderer?.close()
                    pdfRenderer = r
                    isRendererLoading = false
                }
            }
        } else {
            pdfRenderer?.close()
            pdfRenderer = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
        }
    }

    // Current page / chapter count
    val totalChapters = (
        if (isPdf && (pdfRenderer?.pageCount ?: 0) > 0) {
            pdfRenderer!!.pageCount
        } else {
            parsedBook?.chapters?.size ?: 1
        }
    ).coerceAtLeast(1)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragValue by remember { mutableFloatStateOf(0f) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var jumpToPageInput by remember { mutableStateOf("") }

    val initialPage = remember(bookId) {
        val loc = book?.progressLocation?.toIntOrNull() ?: 0
        loc.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (totalChapters - 1).coerceAtLeast(0)),
        pageCount = { totalChapters }
    )

    // Restore reading location only once upon opening the book
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

    // Auto-update reading progress when page changes
    LaunchedEffect(pagerState.currentPage, totalChapters) {
        if (!isLoading && book != null && totalChapters > 0) {
            val currentIdx = pagerState.currentPage
            val percent = ((currentIdx + 1).toFloat() / totalChapters.toFloat()) * 100f
            val chapterTitle = if (isPdf) {
                "Page ${currentIdx + 1}"
            } else {
                parsedBook?.chapters?.getOrNull(currentIdx)?.title ?: ""
            }
            val status = if (percent >= 99f) "FINISHED" else "READING"
            bookRepository.updateProgress(
                bookId = book.id,
                percent = percent,
                location = currentIdx.toString(),
                chapterTitle = chapterTitle,
                status = status
            )
        }
    }

    // Current page bookmark check
    val isCurrentPageBookmarked = remember(bookmarks, pagerState.currentPage) {
        bookmarks.any { it.pageIndex == pagerState.currentPage }
    }

    val currentChapter = parsedBook?.chapters?.getOrNull(pagerState.currentPage)

    // Sync TTS content when chapter changes or TTS sheet opens
    LaunchedEffect(pagerState.currentPage, parsedBook) {
        currentChapter?.let { ch ->
            if (ch.plainText.isNotBlank()) {
                ttsManager.setContent(ch.plainText)
            }
        }
    }

    // Auto-advance to next page when reading finishes current page
    LaunchedEffect(pagerState, totalChapters, parsedBook) {
        ttsManager.onPageFinishedListener = {
            if (pagerState.currentPage < totalChapters - 1) {
                scope.launch {
                    val next = pagerState.currentPage + 1
                    pagerState.animateScrollToPage(next)
                    val nextCh = parsedBook?.chapters?.getOrNull(next)
                    nextCh?.let { ch ->
                        ttsManager.setContent(ch.plainText)
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
                Text("Loading book pages...", color = textColor)
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("reader_root")
    ) {
        // PDF or Reflowable Book Viewer
        if (isPdf && pdfDisplayMode == PdfDisplayMode.TEXT) {
            // PDF Text Mode: reflowable text with word-level cursor selection & high readability
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = (if (currentTheme == ReaderTheme.OLED) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.95f),
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
                            Icon(Icons.Default.Article, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF Text & Cursor Selection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AmberGold)
                        }
                        TextButton(
                            onClick = { pdfDisplayMode = PdfDisplayMode.PAGE },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Page", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val chapter = parsedBook?.chapters?.getOrNull(pageIndex)
                    if (chapter != null) {
                        ChapterPageView(
                            chapter = chapter,
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
                            activeSelection = activeCursorSelection,
                            onSelectionChange = { activeCursorSelection = it },
                            highlights = highlights.filter { it.pageIndex == pageIndex },
                            isTtsActive = isTtsPlaying,
                            activeTtsSentence = currentTtsSentence
                        )
                    }
                }
            }
        } else if (isPdf) {
            if (settings.readingMode == ReadingMode.PAGINATED) {
                // Paginated Horizontal Pager for PDF - supports swiping left/right, tap page turns, pinch-to-zoom!
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf_horizontal_pager")
                ) { pageIndex ->
                    PdfSinglePageView(
                        pageIndex = pageIndex,
                        pdfRenderer = pdfRenderer,
                        theme = currentTheme,
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
                        onOpenTextMode = {
                            pdfDisplayMode = PdfDisplayMode.TEXT
                        },
                        onStartTts = {
                            val ch = parsedBook?.chapters?.getOrNull(pageIndex)
                            if (ch != null && ch.plainText.isNotBlank()) {
                                ttsManager.setContent(ch.plainText)
                                ttsManager.play()
                            }
                            showTtsSheet = true
                        }
                    )
                }
            } else {
                // Continuous Vertical Scroll Mode for PDF
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
            // Reflowable EPUB / TXT Reader
            if (settings.readingMode == ReadingMode.PAGINATED) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val chapter = parsedBook?.chapters?.getOrNull(pageIndex)
                    if (chapter != null) {
                        ChapterPageView(
                            chapter = chapter,
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
                            activeSelection = activeCursorSelection,
                            onSelectionChange = { activeCursorSelection = it },
                            highlights = highlights.filter { it.pageIndex == pageIndex },
                            isTtsActive = isTtsPlaying,
                            activeTtsSentence = currentTtsSentence
                        )
                    }
                }
            } else {
                // Continuous Vertical Scroll Mode
                ContinuousScrollView(
                    parsedBook = parsedBook,
                    settings = settings,
                    textColor = textColor,
                    fontFamily = resolvedFontFamily,
                    onCenterTap = { showChrome = !showChrome },
                    activeSelection = activeCursorSelection,
                    onSelectionChange = { activeCursorSelection = it },
                    highlights = highlights,
                    isTtsActive = isTtsPlaying,
                    activeTtsSentence = currentTtsSentence
                )
            }
        }

        // Warm Night Light Tint Overlay
        if (settings.nightLightWarmth > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFB300).copy(alpha = settings.nightLightWarmth * 0.35f))
            )
        }

        // Top Reading Chrome (Auto-hiding)
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

                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentChapter?.title ?: "Page ${pagerState.currentPage + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bookmark Toggle
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isCurrentPageBookmarked) {
                                    val bm = bookmarks.find { it.pageIndex == pagerState.currentPage }
                                    if (bm != null) bookRepository.removeBookmark(bm.id)
                                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                } else {
                                    val snippet = currentChapter?.formattedParagraphs?.firstOrNull()?.take(80) ?: ""
                                    bookRepository.addBookmark(
                                        Bookmark(
                                            bookId = book.id,
                                            location = "page:${pagerState.currentPage}",
                                            pageIndex = pagerState.currentPage,
                                            chapterTitle = currentChapter?.title ?: "",
                                            snippetText = snippet
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

                    // Table of Contents
                    IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("toc_button")) {
                        Icon(Icons.Default.List, contentDescription = "Table of Contents")
                    }

                    // Search Book
                    IconButton(onClick = { showSearchSheet = true }, modifier = Modifier.testTag("in_book_search_button")) {
                        Icon(Icons.Default.Search, contentDescription = "Search Book")
                    }

                    // PDF Mode Toggle (Page Image vs Extracted Text & Selection)
                    if (isPdf) {
                        IconButton(
                            onClick = {
                                pdfDisplayMode = if (pdfDisplayMode == PdfDisplayMode.PAGE) PdfDisplayMode.TEXT else PdfDisplayMode.PAGE
                            },
                            modifier = Modifier.testTag("pdf_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (pdfDisplayMode == PdfDisplayMode.PAGE) Icons.Default.Article else Icons.Default.Description,
                                contentDescription = if (pdfDisplayMode == PdfDisplayMode.PAGE) "Text & Cursor Selection Mode" else "Visual Page Mode",
                                tint = if (pdfDisplayMode == PdfDisplayMode.TEXT) AmberGold else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Audio Narrator (TTS)
                    IconButton(onClick = { showTtsSheet = true }, modifier = Modifier.testTag("tts_button")) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Audio Narrator",
                            tint = if (isTtsPlaying) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Font & Appearance (Aa)
                    IconButton(onClick = { showAppearanceSheet = true }, modifier = Modifier.testTag("appearance_button")) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font & Display Settings")
                    }
                }
            }
        }

        // Bottom Reading Chrome (Auto-hiding Scrubber & Stats)
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
                    // Location & Progress Info (tap to jump to page)
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
                        val displayPage = (if (isDraggingSlider) sliderDragValue.toInt() else pagerState.currentPage).coerceIn(0, totalChapters - 1)
                        val headerLabel = if (isPdf) {
                            "Page ${displayPage + 1} of $totalChapters (Tap to Jump)"
                        } else {
                            "${currentChapter?.title ?: "Chapter"} (${displayPage + 1} of $totalChapters)"
                        }
                        Text(
                            text = headerLabel,
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

                    // Interactive Progress Scrubber with Quick Turn Arrow Buttons
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
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold
                            ),
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

                    // Estimated reading time
                    val wordsLeft = (totalChapters - pagerState.currentPage) * 300
                    val minutesLeft = (wordsLeft / 220).coerceAtLeast(1)
                    Text(
                        text = "$minutesLeft min left in book",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // Cursor-based Selection Floating Action Bar
        AnimatedVisibility(
            visible = activeCursorSelection != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showChrome) 125.dp else 24.dp)
        ) {
            activeCursorSelection?.let { sel ->
                val currentCh = parsedBook?.chapters?.getOrNull(pagerState.currentPage)
                val paraText = currentCh?.formattedParagraphs?.getOrNull(sel.paragraphIndex) ?: sel.selectedText
                val wordsInPara = remember(paraText) {
                    paraText.split("\\s+".toRegex()).filter { it.isNotBlank() }
                }

                CursorSelectionFloatingBar(
                    selection = sel,
                    totalWordsInParagraph = wordsInPara.size,
                    allParagraphWords = wordsInPara,
                    onNudgeStart = { delta ->
                        val newStart = (sel.startWordIndex + delta).coerceIn(0, sel.endWordIndex)
                        val newText = wordsInPara.subList(newStart, sel.endWordIndex + 1).joinToString(" ")
                        activeCursorSelection = sel.copy(startWordIndex = newStart, selectedText = newText)
                    },
                    onNudgeEnd = { delta ->
                        val newEnd = (sel.endWordIndex + delta).coerceIn(sel.startWordIndex, (wordsInPara.size - 1).coerceAtLeast(sel.startWordIndex))
                        val newText = wordsInPara.subList(sel.startWordIndex, newEnd + 1).joinToString(" ")
                        activeCursorSelection = sel.copy(endWordIndex = newEnd, selectedText = newText)
                    },
                    onSelectSentence = {
                        val fullPara = wordsInPara.joinToString(" ")
                        val sentences = fullPara.split("(?<=[.!?])\\s+".toRegex())
                        val targetSentence = sentences.find { it.contains(sel.selectedText.take(15)) } ?: fullPara
                        activeCursorSelection = sel.copy(selectedText = targetSentence)
                    },
                    onSelectAllParagraph = {
                        activeCursorSelection = sel.copy(
                            startWordIndex = 0,
                            endWordIndex = (wordsInPara.size - 1).coerceAtLeast(0),
                            selectedText = wordsInPara.joinToString(" ")
                        )
                    },
                    onSpeak = { textToSpeak ->
                        ttsManager.speakText(textToSpeak)
                        Toast.makeText(context, "Reading selection aloud", Toast.LENGTH_SHORT).show()
                    },
                    onDefine = { word ->
                        wordLookupResult = DictionaryLookup.getDefinition(word)
                    },
                    onHighlight = { colorHex ->
                        scope.launch {
                            bookRepository.addHighlight(
                                Highlight(
                                    bookId = bookId,
                                    location = "p:${pagerState.currentPage}:para:${sel.paragraphIndex}",
                                    pageIndex = pagerState.currentPage,
                                    colorHex = colorHex,
                                    selectedText = sel.selectedText,
                                    chapterTitle = currentChapter?.title ?: ""
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
                        DictionaryLookup.shareTextOrQuote(context, sel.selectedText, book?.title ?: "", book?.author ?: "")
                        activeCursorSelection = null
                    },
                    onClear = {
                        activeCursorSelection = null
                    }
                )
            }
        }
    }

    // Paragraph Highlight & Lookup Action Modal
    selectedParagraphText?.let { paragraph ->
        AlertDialog(
            onDismissRequest = { selectedParagraphText = null },
            title = {
                Text("Selected Text Action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "“${paragraph.take(120)}...”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Color Highlights Row
                    Text("Highlight Color:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val highlightColors = listOf(
                            Pair(HighlightAmber, "#FFE082"),
                            Pair(HighlightMint, "#A5D6A7"),
                            Pair(HighlightSky, "#90CAF9"),
                            Pair(HighlightRose, "#F48FB1")
                        )
                        highlightColors.forEach { (c, hex) ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable {
                                        scope.launch {
                                            bookRepository.addHighlight(
                                                Highlight(
                                                    bookId = bookId,
                                                    location = "p:${pagerState.currentPage}",
                                                    pageIndex = pagerState.currentPage,
                                                    colorHex = hex,
                                                    selectedText = paragraph,
                                                    chapterTitle = currentChapter?.title ?: ""
                                                )
                                            )
                                            Toast.makeText(context, "Highlighted text", Toast.LENGTH_SHORT).show()
                                            selectedParagraphText = null
                                        }
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Add Note
                        TextButton(onClick = {
                            activeNoteInput = ""
                            showNoteDialog = true
                        }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Note")
                        }

                        // Dictionary definition
                        TextButton(onClick = {
                            val firstWord = paragraph.split("\\s+".toRegex()).firstOrNull() ?: ""
                            wordLookupResult = DictionaryLookup.getDefinition(firstWord)
                        }) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Define")
                        }

                        // Share quote
                        TextButton(onClick = {
                            DictionaryLookup.shareTextOrQuote(context, paragraph, book.title, book.author)
                            selectedParagraphText = null
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedParagraphText = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Word definition popup
    wordLookupResult?.let { def ->
        AlertDialog(
            onDismissRequest = { wordLookupResult = null },
            title = {
                Text(text = def.word.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = def.partOfSpeech, fontSize = 12.sp, color = AmberGold, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = def.definition, style = MaterialTheme.typography.bodyMedium)
                    if (def.example != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Example: “${def.example}”", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row {
                        Button(
                            onClick = {
                                DictionaryLookup.openWikipedia(context, def.word)
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
                                DictionaryLookup.openWebSearch(context, def.word)
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
                TextButton(onClick = { wordLookupResult = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Jump to Page Dialog (Direct quick jump for large books)
    if (showJumpToPageDialog) {
        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text(
                        "Enter page number between 1 and $totalChapters:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jumpToPageInput,
                        onValueChange = { jumpToPageInput = it.filter { ch -> ch.isDigit() } },
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
                            val targetIndex = enteredPage - 1
                            scope.launch { pagerState.scrollToPage(targetIndex) }
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

    // Attach Note Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Attach Note to Highlight") },
            text = {
                Column {
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
                    onClick = {
                        val p = selectedParagraphText ?: ""
                        scope.launch {
                            bookRepository.addHighlight(
                                Highlight(
                                    bookId = bookId,
                                    location = "p:${pagerState.currentPage}",
                                    pageIndex = pagerState.currentPage,
                                    colorHex = "#FFE082",
                                    selectedText = p,
                                    note = activeNoteInput,
                                    chapterTitle = currentChapter?.title ?: ""
                                )
                            )
                            Toast.makeText(context, "Note attached to highlight", Toast.LENGTH_SHORT).show()
                            showNoteDialog = false
                            selectedParagraphText = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Save Note", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Font & Appearance BottomSheet
    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AppearanceSettingsContent(
                settings = settings,
                onSettingsChange = { updated ->
                    settingsRepository.updateSettings(updated)
                }
            )
        }
    }

    // Table of Contents & Bookmarks Drawer Sheet
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
                        pagerState.scrollToPage(index)
                        showTocSheet = false
                    }
                },
                onDeleteBookmark = { id -> scope.launch { bookRepository.removeBookmark(id) } },
                onDeleteHighlight = { id -> scope.launch { bookRepository.removeHighlight(id) } }
            )
        }
    }

    // In-Book Search Sheet
    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            InBookSearchContent(
                parsedBook = parsedBook,
                onSelectMatch = { chapterIdx ->
                    scope.launch {
                        pagerState.scrollToPage(chapterIdx)
                        showSearchSheet = false
                    }
                }
            )
        }
    }

    // TTS Audio Narrator Sheet
    if (showTtsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTtsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            val pageLabel = if (isPdf) "Page ${pagerState.currentPage + 1} of $totalChapters" else (currentChapter?.title ?: "Page ${pagerState.currentPage + 1}")
            TtsAudioControlsContent(
                pageTitle = pageLabel,
                isPlaying = isTtsPlaying,
                currentSentence = currentTtsSentence,
                sentences = sentencesFlow,
                sleepTimerMins = sleepTimerMins,
                speed = settings.ttsSpeed,
                onPlayPause = {
                    if (isTtsPlaying) {
                        ttsManager.pause()
                    } else {
                        if (currentChapter != null && currentChapter.plainText.isNotBlank()) {
                            ttsManager.setContent(currentChapter.plainText)
                        }
                        ttsManager.play()
                    }
                },
                onPrev = { ttsManager.previousSentence() },
                onNext = { ttsManager.nextSentence() },
                onSelectSentence = { idx -> ttsManager.jumpToSentence(idx) },
                onSpeedChange = { newSpeed ->
                    ttsManager.setSpeed(newSpeed)
                    settingsRepository.updateSettings(settings.copy(ttsSpeed = newSpeed))
                },
                onSetSleepTimer = { mins -> ttsManager.setSleepTimer(mins) },
                onCancelSleepTimer = { ttsManager.cancelSleepTimer() }
            )
        }
    }
}

/**
 * Renders an individual chapter page with reflowable Kindle typography.
 */
@Composable
fun ChapterPageView(
    chapter: com.example.data.parser.SpineChapter,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
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
                        if (offset.x < width * 0.22f) {
                            onPrevPage()
                        } else if (offset.x > width * 0.78f) {
                            onNextPage()
                        } else {
                            onCenterTap()
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
            // Chapter Title Heading
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = chapter.title,
                fontFamily = fontFamily,
                fontSize = (settings.fontSizeSp + 6).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = if (settings.textAlignJustify) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Chapter Paragraphs with Cursor Selection & TTS Highlighting
            chapter.formattedParagraphs.forEachIndexed { pIdx, paragraph ->
                val highlight = highlights.find { it.selectedText == paragraph || it.location.endsWith(":para:$pIdx") }
                val isSentenceHighlighted = isTtsActive && pIdx == activeTtsSentence

                val highlightColor = highlight?.let {
                    try {
                        Color(android.graphics.Color.parseColor(it.colorHex))
                    } catch (e: Exception) {
                        AmberGold
                    }
                }

                SelectableParagraphWithCursors(
                    paragraphIndex = pIdx,
                    text = paragraph,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
                    textColor = textColor,
                    fontFamily = fontFamily,
                    textAlign = if (settings.textAlignJustify) TextAlign.Justify else TextAlign.Start,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    highlightColor = highlightColor,
                    isTtsSentenceActive = isSentenceHighlighted
                )

                // If note attached, show subtle badge
                if (highlight?.note != null) {
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
                            text = highlight.note,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
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
 * Continuous Vertical Scroll Reader Mode
 */
@Composable
fun ContinuousScrollView(
    parsedBook: ParsedBook?,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
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
        itemsIndexed(chapters) { chIdx, chapter ->
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = chapter.title,
                fontFamily = fontFamily,
                fontSize = (settings.fontSizeSp + 6).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )

            chapter.formattedParagraphs.forEachIndexed { pIdx, paragraph ->
                val highlight = highlights.find { it.selectedText == paragraph || it.location.contains("ch:$chIdx:para:$pIdx") }
                val isSentenceHighlighted = isTtsActive && pIdx == activeTtsSentence

                val highlightColor = highlight?.let {
                    try {
                        Color(android.graphics.Color.parseColor(it.colorHex))
                    } catch (e: Exception) {
                        AmberGold
                    }
                }

                SelectableParagraphWithCursors(
                    paragraphIndex = pIdx,
                    text = paragraph,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp,
                    textColor = textColor,
                    fontFamily = fontFamily,
                    textAlign = if (settings.textAlignJustify) TextAlign.Justify else TextAlign.Start,
                    activeSelection = activeSelection,
                    onSelectionChange = onSelectionChange,
                    highlightColor = highlightColor,
                    isTtsSentenceActive = isSentenceHighlighted
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Individual high-fidelity PDF page view supporting pinch-to-zoom, pan, tap page turns,
 * and double-tap zoom.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    theme: ReaderTheme,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenTextMode: () -> Unit = {},
    onStartTts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember(pageIndex, pdfRenderer) { mutableStateOf(true) }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageIndex, pdfRenderer) {
        if (pdfRenderer == null) {
            isLoadingPage = true
            return@LaunchedEffect
        }
        isLoadingPage = true
        withContext(Dispatchers.IO) {
            val bmp = pdfRenderer.renderPage(pageIndex, targetWidth = 960)
            withContext(Dispatchers.Main) {
                pageBitmap = bmp
                isLoadingPage = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pageIndex, scale) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                        }
                    },
                    onTap = { tapOffset ->
                        if (scale > 1.05f) {
                            onCenterTap()
                        } else {
                            val width = size.width
                            when {
                                tapOffset.x < width * 0.22f -> onPrevPage()
                                tapOffset.x > width * 0.78f -> onNextPage()
                                else -> onCenterTap()
                            }
                        }
                    }
                )
            }
            .then(
                if (scale > 1.05f) {
                    Modifier.pointerInput(pageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                            scale = newScale
                            if (newScale <= 1.05f) {
                                offset = Offset.Zero
                            } else {
                                offset = Offset(offset.x + pan.x, offset.y + pan.y)
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        } else if (isLoadingPage) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Rendering Page ${pageIndex + 1}...",
                    fontSize = 12.sp,
                    color = Color(theme.textHex).copy(alpha = 0.7f)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Page ${pageIndex + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(theme.textHex)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Visual rendering unavailable for this page. You can read the full text content in Text Mode.",
                    fontSize = 13.sp,
                    color = Color(theme.textHex).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenTextMode,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Read in Text & TTS Mode", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Floating pill for PDF quick actions: Listen (TTS) & Cursor Selection
        if (scale <= 1.05f) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = (if (theme == ReaderTheme.OLED) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onStartTts() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Aloud", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                    }

                    Box(
                        modifier = Modifier
                            .size(1.dp, 16.dp)
                            .background(Color.Gray.copy(alpha = 0.4f))
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenTextMode() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Continuous vertical scroll viewer for PDF documents.
 */
@Composable
fun PdfContinuousScrollView(
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    totalPages: Int,
    initialPage: Int,
    theme: ReaderTheme,
    onPageVisible: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageVisible(listState.firstVisibleItemIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCenterTap() })
            },
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(totalPages) { pageIndex ->
            PdfContinuousPageCard(
                pageIndex = pageIndex,
                pdfRenderer = pdfRenderer,
                theme = theme,
                onCenterTap = onCenterTap
            )
        }
    }
}

/**
 * Single page item inside continuous scroll.
 */
@Composable
fun PdfContinuousPageCard(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    theme: ReaderTheme,
    onCenterTap: () -> Unit
) {
    var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember(pageIndex, pdfRenderer) { mutableStateOf(true) }

    LaunchedEffect(pageIndex, pdfRenderer) {
        isLoadingPage = true
        withContext(Dispatchers.IO) {
            val bmp = pdfRenderer?.renderPage(pageIndex, targetWidth = 840)
            withContext(Dispatchers.Main) {
                pageBitmap = bmp
                isLoadingPage = false
            }
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (theme == ReaderTheme.OLED) Color.Black else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { onCenterTap() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isLoadingPage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(32.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Error loading page ${pageIndex + 1}")
                }
            }

            Text(
                text = "Page ${pageIndex + 1}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

/**
 * Font & Appearance Settings Sheet (Kindle 'Aa' Menu)
 */
@Composable
fun AppearanceSettingsContent(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text("Display & Reading Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Themes Row (Light, Sepia, Night, OLED)
        Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReaderTheme.values().forEach { theme ->
                val isSelected = settings.theme == theme
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(theme.bgHex))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AmberGold else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSettingsChange(settings.copy(theme = theme)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = theme.title,
                        color = Color(theme.textHex),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Font Family Selector
        Text("Font Family", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderFont.values().forEach { font ->
                val isSelected = settings.fontFamily == font
                FilterChip(
                    selected = isSelected,
                    onClick = { onSettingsChange(settings.copy(fontFamily = font)) },
                    label = { Text(font.displayName, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Font Size Stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Font Size", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (settings.fontSizeSp > 12f) {
                            onSettingsChange(settings.copy(fontSizeSp = settings.fontSizeSp - 2f))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("A-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = "${settings.fontSizeSp.toInt()} sp",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        if (settings.fontSizeSp < 32f) {
                            onSettingsChange(settings.copy(fontSizeSp = settings.fontSizeSp + 2f))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("A+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Line Spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Line Spacing", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row {
                listOf(1.2f, 1.4f, 1.8f).forEach { spacing ->
                    val isSelected = (settings.lineSpacingMultiplier - spacing).let { kotlin.math.abs(it) < 0.05f }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSettingsChange(settings.copy(lineSpacingMultiplier = spacing)) },
                        label = { Text("${spacing}x", fontSize = 11.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Margins
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Margins", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row {
                listOf(Pair(12, "Narrow"), Pair(20, "Normal"), Pair(32, "Wide")).forEach { (m, name) ->
                    FilterChip(
                        selected = settings.marginPaddingDp == m,
                        onClick = { onSettingsChange(settings.copy(marginPaddingDp = m)) },
                        label = { Text(name, fontSize = 11.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Night Light Warmth Slider
        Text("Warm Night Light", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Slider(
            value = settings.nightLightWarmth,
            onValueChange = { onSettingsChange(settings.copy(nightLightWarmth = it)) },
            valueRange = 0.0f..0.6f,
            colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Table of Contents & Bookmarks Drawer
 */
@Composable
fun TableOfContentsContent(
    parsedBook: ParsedBook?,
    currentPage: Int,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onSelectChapter: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHighlight: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chapters, 1: Bookmarks, 2: Notes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(horizontal = 16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            contentColor = AmberGold
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Chapters") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Bookmarks (${bookmarks.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Notes (${highlights.size})") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                // Chapters
                val toc = parsedBook?.tableOfContents ?: emptyList()
                if (toc.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chapters detected")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(toc) { idx, item ->
                            val isCurrent = idx == currentPage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(item.targetIndex) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) AmberGold else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text("Reading", fontSize = 11.sp, color = AmberGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Bookmarks
                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No bookmarks added yet")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(bookmarks) { _, bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(bm.pageIndex) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = bm.chapterTitle.ifEmpty { "Page ${bm.pageIndex + 1}" }, fontWeight = FontWeight.Bold)
                                    if (bm.snippetText.isNotBlank()) {
                                        Text(text = bm.snippetText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Notes & Highlights
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No highlights or notes yet")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(highlights) { _, hl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectChapter(hl.pageIndex) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(hl.colorHex)))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = hl.chapterTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { onDeleteHighlight(hl.id) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(text = "“${hl.selectedText.take(100)}”", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    if (!hl.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Note: ${hl.note}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Text In-Book Search Sheet
 */
@Composable
fun InBookSearchContent(
    parsedBook: ParsedBook?,
    onSelectMatch: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val chapters = parsedBook?.chapters ?: emptyList()

    val matches = remember(query, chapters) {
        if (query.length < 2) emptyList()
        else {
            val list = mutableListOf<Triple<Int, String, String>>() // ChapterIdx, ChapterTitle, Snippet
            chapters.forEachIndexed { idx, ch ->
                val text = ch.plainText
                var startIndex = 0
                while (startIndex < text.length) {
                    val found = text.indexOf(query, startIndex, ignoreCase = true)
                    if (found != -1) {
                        val snippetStart = (found - 30).coerceAtLeast(0)
                        val snippetEnd = (found + query.length + 50).coerceAtMost(text.length)
                        val snippet = "..." + text.substring(snippetStart, snippetEnd).replace("\n", " ") + "..."
                        list.add(Triple(idx, ch.title, snippet))
                        startIndex = found + query.length
                        if (list.size > 50) break // limit matches
                    } else break
                }
            }
            list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search word or passage in book...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("${matches.size} matches found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(matches) { _, match ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectMatch(match.first) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(match.second, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AmberGold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(match.third, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Text-to-Speech Audio Controls Sheet
 */
@Composable
fun TtsAudioControlsContent(
    pageTitle: String,
    isPlaying: Boolean,
    currentSentence: Int,
    sentences: List<String>,
    sleepTimerMins: Int?,
    speed: Float,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectSentence: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = AmberGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Voice Narration (TTS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(pageTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isPlaying) {
                Surface(
                    color = AmberGold.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Reading...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Currently playing sentence preview
        val activeText = sentences.getOrNull(currentSentence) ?: if (sentences.isNotEmpty()) sentences.first() else "No text found on this page."
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (sentences.isNotEmpty()) "Sentence ${currentSentence + 1} of ${sentences.size}" else "Text Preview",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AmberGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“$activeText”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback Controls Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onPrev,
                enabled = currentSentence > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence", modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(20.dp))

            Surface(
                shape = CircleShape,
                color = AmberGold,
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onPlayPause() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            IconButton(
                onClick = onNext,
                enabled = sentences.isNotEmpty() && currentSentence < sentences.size - 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next sentence", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed Stepper
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { s ->
                FilterChip(
                    selected = (speed - s).let { kotlin.math.abs(it) < 0.05f },
                    onClick = { onSpeedChange(s) },
                    label = { Text("${s}x", fontSize = 11.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sleep Timer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (sleepTimerMins != null) "Sleep Timer: $sleepTimerMins min left" else "Sleep Timer: Off",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (sleepTimerMins != null) {
                TextButton(onClick = onCancelSleepTimer) { Text("Cancel", fontSize = 11.sp) }
            } else {
                TextButton(onClick = { onSetSleepTimer(15) }) { Text("15m", fontSize = 11.sp) }
                TextButton(onClick = { onSetSleepTimer(30) }) { Text("30m", fontSize = 11.sp) }
                TextButton(onClick = { onSetSleepTimer(45) }) { Text("45m", fontSize = 11.sp) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
