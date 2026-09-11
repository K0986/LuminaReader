package com.example.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberGold

/** A selection inside reflowable text: which paragraph, and which characters of it. */
data class TextSelection(
    val paragraphIndex: Int,
    val range: IntRange,
    val text: String
)

/**
 * A paragraph that supports long-press word selection with draggable handles.
 *
 * The paragraph is rendered as a *single* [Text]. That matters for three reasons: justification and
 * hyphenation work again, a long page no longer costs one composable per word, and screen readers
 * announce a sentence instead of a stream of disconnected words. Touches are mapped to characters
 * with [TextLayoutResult.getOffsetForPosition], and [TextSelectionMath] turns a character offset
 * into the surrounding word.
 */
@Composable
fun SelectableParagraph(
    paragraphIndex: Int,
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    textColor: Color,
    fontFamily: FontFamily,
    textAlign: TextAlign,
    activeSelection: TextSelection?,
    onSelectionChange: (TextSelection?) -> Unit,
    highlightColor: Color?,
    isTtsSentenceActive: Boolean,
    modifier: Modifier = Modifier
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val selection = activeSelection?.takeIf { it.paragraphIndex == paragraphIndex }

    val paragraphBg = when {
        isTtsSentenceActive -> AmberGold.copy(alpha = 0.30f)
        highlightColor != null -> highlightColor.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    val annotated = remember(text, selection) {
        buildAnnotatedString {
            append(text)
            selection?.let { sel ->
                val start = sel.range.first.coerceIn(0, text.length)
                val end = (sel.range.last + 1).coerceIn(start, text.length)
                addStyle(
                    SpanStyle(
                        background = AmberGold.copy(alpha = 0.45f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    start,
                    end
                )
            }
        }
    }

    fun select(range: IntRange?) {
        if (range == null) return
        onSelectionChange(
            TextSelection(
                paragraphIndex = paragraphIndex,
                range = range,
                text = TextSelectionMath.substring(text, range)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(paragraphBg)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Text(
            text = annotated,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            color = textColor,
            textAlign = textAlign,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .selectWordOnLongPress(text) { position ->
                    val result = layout ?: return@selectWordOnLongPress
                    val offset = result.getOffsetForPosition(position)
                    select(TextSelectionMath.wordRangeAt(text, offset))
                }
        )

        val result = layout
        if (selection != null && result != null) {
            SelectionHandle(
                anchorOffset = selection.range.first,
                layout = result,
                atStart = true,
                onDragToOffset = { offset ->
                    val end = selection.range.last
                    select(minOf(offset, end)..end)
                }
            )
            SelectionHandle(
                anchorOffset = selection.range.last,
                layout = result,
                atStart = false,
                onDragToOffset = { offset ->
                    val start = selection.range.first
                    select(start..maxOf(offset, start))
                }
            )
        }
    }
}

/**
 * Detects a long press without swallowing plain taps.
 *
 * [androidx.compose.foundation.gestures.detectTapGestures] consumes the initial press, which would
 * stop the reader's tap-to-turn-page zones from ever firing over a paragraph. Here the press is
 * only consumed once it has actually become a long press.
 */
private fun Modifier.selectWordOnLongPress(key: Any?, onLongPress: (Offset) -> Unit): Modifier =
    pointerInput(key) {
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
            if (longPressed) {
                down.consume()
                onLongPress(down.position)
            }
        }
    }

/** Kindle-style round drag handle sitting under one edge of the selection. */
@Composable
private fun SelectionHandle(
    anchorOffset: Int,
    layout: TextLayoutResult,
    atStart: Boolean,
    onDragToOffset: (Int) -> Unit
) {
    val density = LocalDensity.current
    val handleSize = 18.dp
    val handlePx = with(density) { handleSize.toPx() }

    val box = remember(anchorOffset, layout, atStart) {
        val safeOffset = anchorOffset.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
        layout.getBoundingBox(safeOffset)
    }
    val anchorX = if (atStart) box.left else box.right
    val anchorY = box.bottom

    var drag by remember(anchorOffset, atStart) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset((anchorX - handlePx / 2f).toInt(), (anchorY - handlePx / 4f).toInt()) }
            .size(handleSize)
            .clip(CircleShape)
            .background(AmberGold)
            .pointerInput(anchorOffset, atStart, layout) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = { drag = Offset.Zero }
                ) { change, dragAmount ->
                    change.consume()
                    drag += dragAmount
                    val probe = Offset(anchorX + drag.x, anchorY + drag.y - handlePx / 2f)
                    onDragToOffset(layout.getOffsetForPosition(probe))
                }
            }
    )
}
