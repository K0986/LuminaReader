package com.example.ui.screens.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.ReaderTheme
import com.example.data.parser.ParsedBook
import com.example.data.parser.PdfBookParser
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * One page of selectable, reflowable prose.
 *
 * Serves EPUB and TXT chapters as well as a PDF's extracted text layer, so the three
 * formats share a single typography and selection implementation.
 */
@Composable
fun TextPageView(
    page: ReaderPage,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
    highlightsFor: (paragraphIndex: Int, paragraphText: String) -> List<ParagraphHighlight>,
    isTtsActive: Boolean,
    activeTtsSentence: Int,
    onViewPageImage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            // Edge taps turn the page. This sits *behind* the paragraphs, so a tap that
            // lands on a word is handled by the selector instead -- which is what you
            // want when you are trying to select a word near the margin.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            offset.x < size.width * 0.22f -> onPrevPage()
                            offset.x > size.width * 0.78f -> onNextPage()
                            else -> onCenterTap()
                        }
                    }
                )
            }
            .padding(horizontal = settings.marginPaddingDp.dp, vertical = 20.dp)
    ) {
        when (page.status) {
            ReaderPage.Status.LOADING -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(32.dp))
            }

            ReaderPage.Status.NO_TEXT -> NoTextLayerNotice(
                pageTitle = page.title,
                textColor = textColor,
                onViewPageImage = onViewPageImage
            )

            ReaderPage.Status.READY -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = page.title,
                    fontFamily = fontFamily,
                    fontSize = (settings.fontSizeSp + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = if (settings.textAlignJustify) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )

                page.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
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
                        highlights = highlightsFor(paragraphIndex, paragraph),
                        isTtsSentenceActive = isTtsActive && paragraphIndex == activeTtsSentence
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

/**
 * Shown when a PDF page carries no machine-readable text. Saying this plainly is far
 * more useful than the old behaviour, which presented "Page 7 of 412" as the page's
 * prose and then read it aloud.
 */
@Composable
private fun NoTextLayerNotice(
    pageTitle: String,
    textColor: Color,
    onViewPageImage: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ImageNotSupported,
            contentDescription = null,
            tint = AmberGold,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(pageTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This page has no selectable text. It is most likely a scan or an " +
                "image-only page, so there is nothing to select, search or narrate here.",
            fontSize = 13.sp,
            color = textColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        if (onViewPageImage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onViewPageImage,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
            ) {
                Icon(
                    Icons.Default.Description, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("View the page image", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ContinuousScrollView(
    parsedBook: ParsedBook?,
    settings: AppSettings,
    textColor: Color,
    fontFamily: FontFamily,
    onCenterTap: () -> Unit,
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
    highlightsFor: (chapterIndex: Int, paragraphIndex: Int, paragraphText: String) -> List<ParagraphHighlight>,
    isTtsActive: Boolean = false,
    activeTtsSentence: Int = -1
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onCenterTap() }) }
            .padding(horizontal = settings.marginPaddingDp.dp)
    ) {
        itemsIndexed(parsedBook?.chapters ?: emptyList()) { chapterIndex, chapter ->
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

            chapter.formattedParagraphs.forEachIndexed { paragraphIndex, paragraph ->
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
                    highlights = highlightsFor(chapterIndex, paragraphIndex, paragraph),
                    isTtsSentenceActive = isTtsActive && paragraphIndex == activeTtsSentence
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * A single PDF page: pinch to zoom, double-tap to toggle zoom, tap the margins to turn.
 *
 * The page is rendered at the **measured width of the viewport multiplied by the current
 * zoom**, rather than at a hard-coded 960 px. Previously a zoomed page was the same
 * 960 px bitmap stretched by `graphicsLayer`, so zooming in to read a figure caption
 * only ever produced larger blurry pixels. Re-rendering at the zoomed resolution is what
 * makes zoom actually useful.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    rendererFailed: Boolean,
    theme: ReaderTheme,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenTextMode: () -> Unit = {},
    onStartTts: () -> Unit = {},
    hasTextLayer: Boolean = true,
    modifier: Modifier = Modifier
) {
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        // Cap the render width so a 3.5x pinch on a tablet cannot ask for a bitmap
        // larger than the renderer's own ceiling.
        val renderWidth = (viewportWidthPx * scale).toInt().coerceAtMost(viewportWidthPx * 3)

        var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
        var isRendering by remember(pageIndex, pdfRenderer) { mutableStateOf(pdfRenderer != null) }

        LaunchedEffect(pageIndex, pdfRenderer, renderWidth) {
            val renderer = pdfRenderer
            if (renderer == null) {
                // The previous code returned early here while leaving `isLoadingPage`
                // true, which left a spinner turning forever whenever the document
                // failed to open.
                isRendering = false
                pageBitmap = null
                return@LaunchedEffect
            }
            renderer.cachedPage(pageIndex, renderWidth)?.let {
                pageBitmap = it
                isRendering = false
                return@LaunchedEffect
            }
            isRendering = pageBitmap == null
            val rendered = withContext(Dispatchers.IO) { renderer.renderPage(pageIndex, renderWidth) }
            if (rendered != null) pageBitmap = rendered
            isRendering = false
        }

        // Warm the neighbours at fit width so a swipe lands on a drawn page.
        LaunchedEffect(pageIndex, pdfRenderer) {
            val renderer = pdfRenderer ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                listOf(pageIndex + 1, pageIndex - 1)
                    .filter { it in 0 until renderer.pageCount }
                    .forEach { renderer.renderPage(it, viewportWidthPx) }
            }
        }

        Box(
            modifier = Modifier
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
                        onTap = { tap ->
                            if (scale > 1.05f) {
                                onCenterTap()
                            } else {
                                when {
                                    tap.x < size.width * 0.22f -> onPrevPage()
                                    tap.x > size.width * 0.78f -> onNextPage()
                                    else -> onCenterTap()
                                }
                            }
                        }
                    )
                }
                .pointerInput(pageIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                        scale = newScale
                        offset = if (newScale <= 1.05f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = pageBitmap
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // The bitmap already carries the zoomed resolution; only the
                            // pan translation needs to be applied here.
                            translationX = offset.x
                            translationY = offset.y
                        }
                )

                isRendering -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Rendering page ${pageIndex + 1}...",
                        fontSize = 12.sp,
                        color = Color(theme.textHex).copy(alpha = 0.7f)
                    )
                }

                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (rendererFailed) "This PDF could not be opened"
                        else "Page ${pageIndex + 1} could not be rendered",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(theme.textHex)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (rendererFailed) {
                            "The file may be corrupt, password protected, or no longer " +
                                "available at its original location."
                        } else {
                            "Rendering failed for this page. Other pages may still work."
                        },
                        fontSize = 13.sp,
                        color = Color(theme.textHex).copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    if (hasTextLayer) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenTextMode,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article, contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read as text", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (scale <= 1.05f && pageBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = (if (theme == ReaderTheme.OLED) Color(0xFF1E293B)
                    else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = hasTextLayer) { onStartTts() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Headphones, contentDescription = null,
                                tint = if (hasTextLayer) AmberGold else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Read aloud", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (hasTextLayer) AmberGold else Color.Gray
                            )
                        }

                        Box(modifier = Modifier.size(1.dp, 16.dp).background(Color.Gray.copy(alpha = 0.4f)))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = hasTextLayer) { onOpenTextMode() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Article, contentDescription = null,
                                tint = if (hasTextLayer) AmberGold else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (hasTextLayer) "Select text" else "No text layer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasTextLayer) MaterialTheme.colorScheme.onSurface else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

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

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPageVisible)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onCenterTap() }) },
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

@Composable
fun PdfContinuousPageCard(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    theme: ReaderTheme,
    onCenterTap: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (theme == ReaderTheme.OLED) Color.Black else Color.White
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable { onCenterTap() }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
            var isRendering by remember(pageIndex, pdfRenderer) { mutableStateOf(pdfRenderer != null) }

            // Reserving the page's real aspect ratio keeps the scroll position stable
            // while bitmaps stream in, instead of every placeholder being 320 dp tall
            // and then jumping.
            val aspect = remember(pageIndex, pdfRenderer) {
                pdfRenderer?.pageAspectRatio(pageIndex) ?: (842f / 595f)
            }

            LaunchedEffect(pageIndex, pdfRenderer, widthPx) {
                val renderer = pdfRenderer
                if (renderer == null) {
                    isRendering = false
                    return@LaunchedEffect
                }
                val rendered = withContext(Dispatchers.IO) { renderer.renderPage(pageIndex, widthPx) }
                pageBitmap = rendered
                isRendering = false
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bitmap = pageBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF page ${pageIndex + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f / aspect),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(32.dp))
                        } else {
                            Text("Page ${pageIndex + 1} unavailable", fontSize = 12.sp, color = Color.Gray)
                        }
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
}
