package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGold
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightRose
import com.example.ui.theme.HighlightSky

/**
 * An active selection, as a half-open character range into one paragraph.
 *
 * [selectedText] is always literally `paragraphText.substring(start, end)`.
 */
data class CursorSelectionState(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val selectedText: String
) {
    val isEmpty: Boolean get() = end <= start

    companion object {
        fun of(paragraphIndex: Int, text: String, range: IntRange): CursorSelectionState? {
            if (range.isEmpty()) return null
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (end <= start) return null
            return CursorSelectionState(paragraphIndex, start, end, text.substring(start, end))
        }

        fun of(paragraphIndex: Int, text: String, start: Int, end: Int): CursorSelectionState? {
            val s = start.coerceIn(0, text.length)
            val e = end.coerceIn(0, text.length)
            if (e <= s) return null
            return CursorSelectionState(paragraphIndex, s, e, text.substring(s, e))
        }
    }
}

/** A saved highlight projected onto the current paragraph. */
data class ParagraphHighlight(
    val start: Int,
    val end: Int,
    val color: Color
)

/**
 * A paragraph rendered as a single laid-out [Text] with word-level touch selection.
 *
 * The previous version exploded each paragraph into one `Text` per word inside a
 * `FlowRow`. That had three consequences worth spelling out:
 *
 * - **Typography was lost.** A `FlowRow` of separate texts has no shared line box, so
 *   `TextAlign.Justify` and `lineHeight` had no effect; the `textAlign` parameter was
 *   accepted and then never used.
 * - **It was slow.** A 500-word paragraph allocated ~1500 layout nodes, and because the
 *   per-word `pointerInput` key included the selection bounds, every tap re-registered
 *   every word's gesture detector.
 * - **Selection could not cross a paragraph**, and tapping could only ever grow it.
 *
 * Here there is exactly one `Text`. The [TextLayoutResult] it reports back is the whole
 * mechanism: it maps a touch position to a character offset, which [WordBoundaries]
 * snaps to a word. Highlights and the live selection are drawn as background spans on
 * an [AnnotatedString], so they follow the real glyph positions on justified lines.
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
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
    highlights: List<ParagraphHighlight>,
    isTtsSentenceActive: Boolean,
    modifier: Modifier = Modifier
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    val isSelectedParagraph = activeSelection?.paragraphIndex == paragraphIndex
    val selection = activeSelection?.takeIf { isSelectedParagraph }

    val annotated = remember(text, selection, highlights, isTtsSentenceActive) {
        buildAnnotatedString {
            append(text)
            highlights.forEach { highlight ->
                val start = highlight.start.coerceIn(0, text.length)
                val end = highlight.end.coerceIn(start, text.length)
                if (end > start) {
                    addStyle(SpanStyle(background = highlight.color.copy(alpha = 0.40f)), start, end)
                }
            }
            if (isTtsSentenceActive) {
                addStyle(SpanStyle(background = AmberGold.copy(alpha = 0.22f)), 0, text.length)
            }
            selection?.let {
                addStyle(
                    SpanStyle(
                        background = AmberGold.copy(alpha = 0.45f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    it.start.coerceIn(0, text.length),
                    it.end.coerceIn(0, text.length)
                )
            }
        }
    }

    /** Which selection edge a drag should move: whichever the finger started nearer. */
    var draggingStartEdge by remember { mutableStateOf(false) }

    fun offsetAt(position: Offset): Int? =
        layout?.getOffsetForPosition(position)?.coerceIn(0, text.length)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(text, paragraphIndex) {
                detectTapGestures(
                    onTap = { position ->
                        val offset = offsetAt(position) ?: return@detectTapGestures
                        if (selection != null && offset in selection.start until selection.end) {
                            // Tapping inside the current selection dismisses it, which is
                            // how every other Android text surface behaves.
                            onSelectionChange(null)
                        } else {
                            onSelectionChange(
                                CursorSelectionState.of(
                                    paragraphIndex,
                                    text,
                                    WordBoundaries.wordRangeAt(text, offset)
                                )
                            )
                        }
                    },
                    onDoubleTap = { position ->
                        val offset = offsetAt(position) ?: return@detectTapGestures
                        onSelectionChange(
                            CursorSelectionState.of(
                                paragraphIndex,
                                text,
                                WordBoundaries.sentenceRangeAt(text, offset)
                            )
                        )
                    }
                )
            }
            .pointerInput(text, paragraphIndex, selection?.start, selection?.end) {
                // Long-press then drag to sweep a range, the same gesture users already
                // know from the system text selector.
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val offset = offsetAt(position) ?: return@detectDragGesturesAfterLongPress
                        val current = selection
                        if (current == null) {
                            draggingStartEdge = false
                            onSelectionChange(
                                CursorSelectionState.of(
                                    paragraphIndex,
                                    text,
                                    WordBoundaries.wordRangeAt(text, offset)
                                )
                            )
                        } else {
                            draggingStartEdge =
                                kotlin.math.abs(offset - current.start) <
                                    kotlin.math.abs(offset - current.end)
                        }
                    },
                    onDrag = { change, _ ->
                        val offset = offsetAt(change.position) ?: return@detectDragGesturesAfterLongPress
                        val current = selection ?: return@detectDragGesturesAfterLongPress
                        val word = WordBoundaries.wordRangeAt(text, offset)
                        if (word.isEmpty()) return@detectDragGesturesAfterLongPress

                        val updated = if (draggingStartEdge) {
                            CursorSelectionState.of(
                                paragraphIndex, text,
                                minOf(word.first, current.end - 1), current.end
                            )
                        } else {
                            CursorSelectionState.of(
                                paragraphIndex, text,
                                current.start, maxOf(word.last + 1, current.start + 1)
                            )
                        }
                        if (updated != null) onSelectionChange(updated)
                        change.consume()
                    }
                )
            }
            // One semantics node for the whole paragraph. Per-word `Text`s made TalkBack
            // announce a book one word at a time.
            .semantics { this.text = AnnotatedString(text) }
    ) {
        Text(
            text = annotated,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            color = textColor,
            textAlign = textAlign,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Floating action bar for the current selection: word-precise edge nudging, plus speak,
 * define, highlight, note, copy and share.
 */
@Composable
fun CursorSelectionFloatingBar(
    selection: CursorSelectionState,
    paragraphText: String,
    onSelectionChange: (CursorSelectionState?) -> Unit,
    onSpeak: (String) -> Unit,
    onDefine: (String) -> Unit,
    onHighlight: (colorHex: String) -> Unit,
    onAddNote: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    fun nudge(edgeIsStart: Boolean, words: Int) {
        val updated = if (edgeIsStart) {
            val newStart = WordBoundaries.nudgeByWord(paragraphText, selection.start, words)
            CursorSelectionState.of(
                selection.paragraphIndex, paragraphText,
                newStart.coerceAtMost(selection.end - 1), selection.end
            )
        } else {
            val newEnd = WordBoundaries.nudgeByWord(paragraphText, selection.end, words)
            CursorSelectionState.of(
                selection.paragraphIndex, paragraphText,
                selection.start, newEnd.coerceAtLeast(selection.start + 1)
            )
        }
        if (updated != null) onSelectionChange(updated)
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val charCount = selection.end - selection.start
                Text(
                    text = "$charCount character${if (charCount == 1) "" else "s"} selected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    EdgeNudger(
                        label = "S",
                        canShrink = selection.start < selection.end - 1,
                        canGrow = selection.start > 0,
                        onGrow = { nudge(edgeIsStart = true, words = -1) },
                        onShrink = { nudge(edgeIsStart = true, words = 1) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    EdgeNudger(
                        label = "E",
                        canShrink = selection.end > selection.start + 1,
                        canGrow = selection.end < paragraphText.length,
                        onGrow = { nudge(edgeIsStart = false, words = 1) },
                        onShrink = { nudge(edgeIsStart = false, words = -1) },
                        growIsForward = true
                    )
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand to sentence / whole paragraph. These were previously wired up in
                // the reader but had no buttons, so they were unreachable.
                ActionChip(Icons.AutoMirrored.Filled.ShortText, "Sentence") {
                    val range = WordBoundaries.sentenceRangeAt(paragraphText, selection.start)
                    CursorSelectionState.of(selection.paragraphIndex, paragraphText, range)
                        ?.let(onSelectionChange)
                }
                ActionChip(Icons.AutoMirrored.Filled.Subject, "Paragraph") {
                    CursorSelectionState.of(
                        selection.paragraphIndex, paragraphText, 0, paragraphText.length
                    )?.let(onSelectionChange)
                }
                ActionChip(Icons.Default.Headphones, "Speak", tint = AmberGold) {
                    onSpeak(selection.selectedText)
                }
                ActionChip(Icons.Default.Translate, "Define") {
                    val firstWord = selection.selectedText.trim().substringBefore(' ')
                    onDefine(firstWord.ifBlank { selection.selectedText })
                }
                ActionChip(
                    Icons.Default.FormatColorFill,
                    "Highlight",
                    tint = AmberGold,
                    selected = showColorPicker
                ) { showColorPicker = !showColorPicker }
                ActionChip(Icons.AutoMirrored.Filled.NoteAdd, "Note", onClick = onAddNote)
                ActionChip(Icons.Default.ContentCopy, "Copy") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Lumina Selection", selection.selectedText)
                    )
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                ActionChip(Icons.Default.Share, "Share", onClick = onShare)
            }

            AnimatedVisibility(visible = showColorPicker) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Save colour:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        HIGHLIGHT_COLORS.forEach { (color, hex) ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, Color.DarkGray.copy(alpha = 0.3f), CircleShape)
                                    .clickable {
                                        onHighlight(hex)
                                        showColorPicker = false
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val HIGHLIGHT_COLORS = listOf(
    HighlightAmber to "#FFE082",
    HighlightMint to "#A5D6A7",
    HighlightSky to "#90CAF9",
    HighlightRose to "#F48FB1"
)

@Composable
private fun EdgeNudger(
    label: String,
    canShrink: Boolean,
    canGrow: Boolean,
    onGrow: () -> Unit,
    onShrink: () -> Unit,
    growIsForward: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val backEnabled = if (growIsForward) canShrink else canGrow
        IconButton(
            onClick = { if (growIsForward) onShrink() else onGrow() },
            enabled = backEnabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Move $label edge left one word",
                tint = if (backEnabled) AmberGold else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
        val forwardEnabled = if (growIsForward) canGrow else canShrink
        IconButton(
            onClick = { if (growIsForward) onGrow() else onShrink() },
            enabled = forwardEnabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Move $label edge right one word",
                tint = if (forwardEnabled) AmberGold else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> AmberGold.copy(alpha = 0.25f)
            tint != null -> AmberGold.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (tint != null) FontWeight.Bold else FontWeight.Medium,
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
