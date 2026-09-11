package com.example.ui.screens.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderTheme
import com.example.data.parser.PdfBookParser
import com.example.data.parser.pdf.PdfPageGeometry
import com.example.data.parser.pdf.PdfPageSize
import com.example.data.parser.pdf.PdfPoint
import com.example.data.parser.pdf.PdfRect
import com.example.data.parser.pdf.PdfSelection
import com.example.data.parser.pdf.PdfTextCapability
import com.example.data.parser.pdf.PdfTextEngine
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext

/** A highlight resolved to page rectangles, ready to paint. */
data class PdfHighlightOverlay(val rects: List<PdfRect>, val color: Color)

private const val MAX_ZOOM = 4f
private const val ZOOMED_THRESHOLD = 1.05f

/**
 * A single PDF page: rendered bitmap, pinch-zoom, tap zones, and word selection straight on the
 * page when the device can tell us where the glyphs are.
 *
 * The zoom transform is applied to an inner box, so both the overlay canvas and the selection
 * touch handling work in un-transformed page coordinates — Compose maps pointer input back through
 * the layer for us, which removes a whole class of "selection is offset when zoomed" bugs.
 */
@Composable
fun PdfSinglePageView(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    textEngine: PdfTextEngine?,
    theme: ReaderTheme,
    selection: PdfSelection?,
    onSelectionChange: (PdfSelection?) -> Unit,
    highlights: List<PdfHighlightOverlay>,
    onZoomChange: (Boolean) -> Unit,
    onCenterTap: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenTextMode: () -> Unit = {},
    onStartTts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
        var pan by remember(pageIndex) { mutableStateOf(Offset.Zero) }
        var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
        var pageSize by remember(pageIndex, pdfRenderer) { mutableStateOf<PdfPageSize?>(null) }
        var isLoadingPage by remember(pageIndex, pdfRenderer) { mutableStateOf(true) }

        // Re-render in discrete steps so a pinch does not trigger a render on every frame, but a
        // zoomed page is still sharp instead of an upscaled thumbnail.
        val renderStep = scale.coerceIn(1f, 3f).let { kotlin.math.ceil(it).toInt() }

        LaunchedEffect(pageIndex, pdfRenderer, renderStep, containerWidthPx) {
            if (pdfRenderer == null || containerWidthPx <= 0f) return@LaunchedEffect
            isLoadingPage = pageBitmap == null
            withContext(Dispatchers.IO) {
                val target = (containerWidthPx * renderStep).toInt()
                val bitmap = pdfRenderer.renderPage(pageIndex, targetWidth = target)
                val size = pdfRenderer.pageSize(pageIndex)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) pageBitmap = bitmap
                    pageSize = size
                    isLoadingPage = false
                }
            }
        }

        LaunchedEffect(pageIndex) {
            snapshotFlow { scale > ZOOMED_THRESHOLD }.collectLatest { onZoomChange(it) }
        }

        // Word selection is resolved off the gesture loop: asking the platform for the word under a
        // point opens the page again, which is far too slow to do while holding pointer input.
        var pendingLongPress by remember(pageIndex) { mutableStateOf<PdfPoint?>(null) }
        val currentSelection by rememberUpdatedState(selection)
        LaunchedEffect(pageIndex, textEngine) {
            snapshotFlow { pendingLongPress }
                .filterNotNull()
                .collectLatest { point ->
                    val engine = textEngine ?: return@collectLatest
                    val resolved = withContext(Dispatchers.IO) {
                        val word = engine.wordAt(pageIndex, point)
                        val anchor = currentSelection
                        when {
                            word == null -> null
                            anchor != null && anchor.pageIndex == pageIndex ->
                                // Long-pressing a second word stretches the selection across
                                // everything in between, the way a highlighter would.
                                engine.selectRange(
                                    pageIndex,
                                    minOf(anchor.startChar, word.startChar),
                                    maxOf(anchor.endChar, word.endChar)
                                ) ?: word
                            else -> word
                        }
                    }
                    pendingLongPress = null
                    if (resolved != null) onSelectionChange(resolved)
                }
        }

        val fit = pageSize?.let { PdfPageGeometry.fit(it, containerWidthPx, containerHeightPx) }
        val canSelectOnPage = textEngine?.capability == PdfTextCapability.TEXT_AND_SELECTION

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                        scale = newScale
                        pan = if (newScale <= 1f) {
                            Offset.Zero
                        } else {
                            val clamped = PdfPageGeometry.clampPan(
                                panX = pan.x + panChange.x,
                                panY = pan.y + panChange.y,
                                scale = newScale,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx
                            )
                            Offset(clamped.x, clamped.y)
                        }
                    }
                }
                .pointerInput(pageIndex, scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                pan = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        },
                        onTap = { tapOffset ->
                            when {
                                selection != null -> onSelectionChange(null)
                                scale > ZOOMED_THRESHOLD -> onCenterTap()
                                tapOffset.x < size.width * 0.22f -> onPrevPage()
                                tapOffset.x > size.width * 0.78f -> onNextPage()
                                else -> onCenterTap()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
            ) {
                val bitmap = pageBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (fit != null) {
                    PdfOverlayCanvas(
                        fit = fit,
                        highlights = highlights,
                        selection = selection
                    )
                }

                if (canSelectOnPage && fit != null && textEngine != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_selection_surface")
                            .selectPdfWordOnLongPress(pageIndex, fit) { point ->
                                pendingLongPress = point
                            }
                    )
                }
            }

            if (pageBitmap == null) {
                PdfPagePlaceholder(
                    pageIndex = pageIndex,
                    theme = theme,
                    isLoading = isLoadingPage,
                    onOpenTextMode = onOpenTextMode
                )
            }

            if (scale <= ZOOMED_THRESHOLD && selection == null) {
                PdfQuickActions(
                    theme = theme,
                    canSelectOnPage = canSelectOnPage,
                    onStartTts = onStartTts,
                    onOpenTextMode = onOpenTextMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfOverlayCanvas(
    fit: com.example.data.parser.pdf.PdfFitBox,
    highlights: List<PdfHighlightOverlay>,
    selection: PdfSelection?
) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        highlights.forEach { overlay ->
            overlay.rects.forEach { rect ->
                val view = PdfPageGeometry.pdfToView(rect, fit)
                drawRect(
                    color = overlay.color.copy(alpha = 0.35f),
                    topLeft = Offset(view.left, view.top),
                    size = Size(view.width, view.height)
                )
            }
        }
        selection?.rects?.forEach { rect ->
            val view = PdfPageGeometry.pdfToView(rect, fit)
            drawRect(
                color = AmberGold.copy(alpha = 0.40f),
                topLeft = Offset(view.left, view.top),
                size = Size(view.width, view.height)
            )
        }
    }
}

/**
 * Long-press a word on the page. Taps are deliberately left unconsumed so the page-turn zones keep
 * working; only a press that becomes a long press is claimed.
 */
private fun Modifier.selectPdfWordOnLongPress(
    key: Any?,
    fit: com.example.data.parser.pdf.PdfFitBox,
    onLongPress: (PdfPoint) -> Unit
): Modifier = pointerInput(key, fit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPressed = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
            }
            false
        } catch (_: PointerEventTimeoutCancellationException) {
            true
        }
        if (longPressed && PdfPageGeometry.isInsidePage(down.position.x, down.position.y, fit)) {
            down.consume()
            onLongPress(PdfPageGeometry.viewToPdf(down.position.x, down.position.y, fit))
        }
    }
}

@Composable
private fun PdfPagePlaceholder(
    pageIndex: Int,
    theme: ReaderTheme,
    isLoading: Boolean,
    onOpenTextMode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Rendering page ${pageIndex + 1}…",
                fontSize = 12.sp,
                color = Color(theme.textHex).copy(alpha = 0.7f)
            )
        } else {
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
                text = "This page could not be rendered. Its text is still available in text mode.",
                fontSize = 13.sp,
                color = Color(theme.textHex).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenTextMode,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Article,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Read in text mode", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PdfQuickActions(
    theme: ReaderTheme,
    canSelectOnPage: Boolean,
    onStartTts: () -> Unit,
    onOpenTextMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = (if (theme == ReaderTheme.OLED) Color(0xFF1E293B) else MaterialTheme.colorScheme.surface)
            .copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
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
                Text("Read aloud", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberGold)
            }

            Box(
                modifier = Modifier
                    .size(1.dp, 16.dp)
                    .background(Color.Gray.copy(alpha = 0.4f))
            )

            if (canSelectOnPage) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Long-press a word", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenTextMode() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Continuous vertical scroll viewer for PDF documents.
 *
 * Each page reserves its true aspect ratio before the bitmap arrives, so the list no longer jumps
 * around as pages finish rendering.
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

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { onPageVisible(it) }
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

@Composable
private fun PdfContinuousPageCard(
    pageIndex: Int,
    pdfRenderer: PdfBookParser.PdfDocumentRenderer?,
    theme: ReaderTheme,
    onCenterTap: () -> Unit
) {
    var pageBitmap by remember(pageIndex, pdfRenderer) { mutableStateOf<Bitmap?>(null) }
    var pageSize by remember(pageIndex, pdfRenderer) { mutableStateOf<PdfPageSize?>(null) }

    LaunchedEffect(pageIndex, pdfRenderer) {
        withContext(Dispatchers.IO) {
            val size = pdfRenderer?.pageSize(pageIndex)
            withContext(Dispatchers.Main) { pageSize = size }
            val bitmap = pdfRenderer?.renderPage(pageIndex, targetWidth = 840)
            withContext(Dispatchers.Main) { pageBitmap = bitmap }
        }
    }

    val aspect = pageSize
        ?.let { if (it.heightPt > 0f) it.widthPt / it.heightPt else null }
        ?: DEFAULT_PAGE_ASPECT

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(32.dp))
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

/** A4-ish, used only until the real page size is known. */
private const val DEFAULT_PAGE_ASPECT = 595f / 842f
